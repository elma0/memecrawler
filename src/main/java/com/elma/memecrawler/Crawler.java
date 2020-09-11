package com.elma.memecrawler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.elma.memecrawler.common.LW;
import com.elma.memecrawler.integration.model.ImageInfo;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Service
public class Crawler {
    private static final Logger LOG = LoggerFactory.getLogger(Crawler.class);
    public final HttpClient.ResponseReceiver<?> httpClient;
    public final ImageMetaRepository imageRepo;

    @Autowired()
    public Crawler(
            @Qualifier("http.client") HttpClient.ResponseReceiver<?> httpClient,
            ImageMetaRepository imageRepo
    ) {
        this.httpClient = httpClient;
        this.imageRepo = imageRepo;
    }

    @KafkaListener(topics = "urls", groupId = "0")
    public void consume(String message) {
        httpClient.uri(message)
                .responseSingle((response, body) -> {
                    String contentType = response.responseHeaders().get("Content-Type");
                    if (contentType != null && contentType.startsWith("image")) {
                        return Mono.defer(body::asByteArray);
                    }
                    return Mono.empty();
                }).toFuture()
                .whenComplete((img, err) -> {
                    if (err != null) {
                        LOG.error("Error requesting file", err);
                        return;
                    }
                    if (img != null && img.length > 0) {
                        int pos = message.lastIndexOf("/");
                        if (pos > 0) {
                            Path p = Paths.get("images", message.substring(pos + 1));
                            LW.wrap(() -> Files.write(p, img, StandardOpenOption.CREATE));
                            try {
                                // yes, it's blocking
                                imageRepo.insert(new ImageInfo(p.getFileName().toString()));
                            } catch (Exception e) {
                                LOG.error("Error saving imgage", e);
                            }
                            LOG.trace("File {} written", p.toAbsolutePath());
                        }
                    }
                });
    }
}
