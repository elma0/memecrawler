package com.elma.memecrawler.integration;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elma.memecrawler.ImageConsumer;
import com.elma.memecrawler.integration.model.RedditContent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import reactor.netty.http.client.HttpClient;

public class RedditStreamer implements Streamer {
    private static final Logger LOG = LoggerFactory.getLogger(RedditStreamer.class);
    private static final int COUNT = 1000;
    private static final int LIMIT = 100;
    private final BloomFilter<CharSequence> bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charset.forName("UTF-8")),
            1000_000_000L);
    private final HttpClient.ResponseReceiver<?> client;
    private final ImageConsumer consumer;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final ObjectMapper om;
    private final String url;

    public RedditStreamer(
            HttpClient.ResponseReceiver<?> client,
            ImageConsumer consumer,
            String url,
            ObjectMapper om
    ) {
        this.consumer = consumer;
        this.client = client;
        this.url = url;
        this.om = om;
    }

    public void start() {
        if (isRunning.get() || !isRunning.compareAndSet(false, true)) {
            return;
        }
        LOG.info("Reddit streamer started");
        request(payload("1")).whenComplete((response, throwable) -> processNext(parseContentAndGetNext(response), 0));
    }

    private void processNext(String next, int attempt) {
        if (next == null || next.isEmpty()) {
            isRunning.compareAndSet(true, false);
            LOG.info("Job finished");
            return;
        }
        CompletableFuture<String> f = request(payload(next));
        f.whenComplete((response, throwable) -> {
                    if (throwable == null) {
                        processNext(parseContentAndGetNext(response), 0);
                    }
                    LOG.warn("Page content requesting error. Next attempt", throwable);
                    if (attempt < 5) {
                        processNext(next, attempt + 1);
                    } else {
                        LOG.error("Page content requesting error", throwable);
                    }
                }
        );
    }

    @Nullable
    public String parseContentAndGetNext(String contentAsString) {
        RedditContent content;
        try {
            content = om.readValue(contentAsString, RedditContent.class);
        } catch (JsonProcessingException e) {
            LOG.error("Invalid page content", e);
            return null;
        }
        if (content.data() == null || content.data().children() == null) {
            return null;
        }
        for (RedditContent childContent : content.data().children()) {
            String parsedUrl = childContent.data().url();
            if (!bloomFilter.mightContain(parsedUrl)) {
                consumer.onImage(parsedUrl);
            }
            bloomFilter.put(parsedUrl);
        }
        return content.data().after();
    }

    private CompletableFuture<String> request(Map<String, ?> params) {
        return client.uri(url + "?" + asString(params))
                .responseSingle((response, body) -> body.asString())
                .log()
                .toFuture();
    }

    private ImmutableMap<String, ? extends Serializable> payload(String page) {
        return ImmutableMap.of("after", page, "count", COUNT, "show", "all", "limit", LIMIT);
    }

    public String asString(Map<String, ?> params) {
        return params.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }
}
