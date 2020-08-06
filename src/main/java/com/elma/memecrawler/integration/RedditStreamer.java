package com.elma.memecrawler.integration;

import java.nio.charset.Charset;
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
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import reactor.netty.http.client.HttpClient;

@Service
public class RedditStreamer implements Streamer {
    private static final Logger LOG = LoggerFactory.getLogger(RedditStreamer.class);
    private static final int COUNT = 1000;
    private static final int LIMIT = 100;
    private final BloomFilter<CharSequence> bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charset.forName("UTF-8")),
            1000_000_000L);
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
        request(ImmutableMap.of("after", 1, "count", COUNT, "show", "all", "limit", LIMIT))
                .whenComplete((response, throwable) ->
                        processNext(Optional.ofNullable(LW.wrap(() -> parseContent(response)).data()).map(RedditData::after).orElse(null),
                                0)
                );
    }

    private void processNext(String next, int attempt) {
        if (next == null || next.isEmpty()) {
            isRunning.compareAndSet(true, false);
            LOG.info("Job finished");
            return;
        }
        request(ImmutableMap.of("after", next, "count", COUNT, "show", "all", "limit", LIMIT))
                .whenComplete((response, throwable) -> {
                            if (throwable != null) {
                                LOG.warn("Error requesting page content. Next attempt", throwable);
                                if (attempt < 5) {
                                    processNext(next, attempt + 1);
                                } else {
                                    LOG.error("Error requesting page content", throwable);
                                }
                            }
                            processNext(Optional.ofNullable(LW.wrap(() -> parseContent(response)).data())
                                    .map(RedditData::after).orElse(null), 0);
                        }
                );
    }

    public RedditContent parseContent(String contentAsString) throws JsonProcessingException {
        RedditContent content = om.readValue(contentAsString, RedditContent.class);
        if (content.data() != null && content.data().children() != null) {
            for (RedditContent childContent : content.data().children()) {
                String parsedUrl = childContent.data().url();
                if (!bloomFilter.mightContain(parsedUrl)) {
                    kafkaTemplate.send(TOPIC, childContent.data().url());
                }
                bloomFilter.put(parsedUrl);
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
