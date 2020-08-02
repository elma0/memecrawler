package com.elma.memecrawler.integration;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.elma.memecrawler.common.LW;
import com.elma.memecrawler.integration.model.RedditContent;
import com.elma.memecrawler.integration.model.RedditData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import reactor.netty.http.client.HttpClient;

@Service
public class RedditStreamer implements Streamer {
    private static final Logger LOG = LoggerFactory.getLogger(RedditStreamer.class);
    private final HttpClient.ResponseReceiver<?> client;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final ObjectMapper om;
    private final String url;

    public RedditStreamer(
            @Qualifier("http.client") HttpClient.ResponseReceiver<?> client,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${reddit.url}") String url,
            ObjectMapper om
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.client = client;
        this.url = url;
        this.om = om;
    }

    public void start() {
        if (isRunning.get() || !isRunning.compareAndSet(false, true)) {
            return;
        }
        LOG.info("Reddit streamer started");
        request(ImmutableMap.of("after", 1, "count", 1000, "show", "all", "limit", 100))
                .whenComplete((response, throwable) -> processNext(response));
    }

    private void processNext(String s) {
        String next = Optional.ofNullable(LW.wrap(() -> parseContent(s)).data()).map(RedditData::after).orElse(null);
        if (next == null || next.isEmpty()) {
            isRunning.compareAndSet(true, false);
            return;
        }
        request(ImmutableMap.of("after", next, "count", 1000, "show", "all", "limit", 100))
                .whenComplete((response, throwable) -> processNext(response));
    }

    public RedditContent parseContent(String contentAsString) throws JsonProcessingException {
        RedditContent content = om.readValue(contentAsString, RedditContent.class);
        if (content.data() != null && content.data().children() != null) {
            for (RedditContent childContent : content.data().children()) {
                kafkaTemplate.send(TOPIC, childContent.data().url());
            }
        }
        return content;
    }

    private CompletableFuture<String> request(Map<String, ?> params) {
        return client.uri(url + "?" + asString(params))
                .responseSingle((response, body) -> body.asString())
                .log()
                .toFuture();
    }

    public String asString(Map<String, ?> params) {
        return params.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }
}
