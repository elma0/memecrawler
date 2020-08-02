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
                    if (response.responseHeaders().get("Content-Type").startsWith("image")) {
                        return Mono.zip(Mono.just(message), Mono.defer(body::asByteArray));
                    }
                    return Mono.zip(Mono.empty(), Mono.empty());
                }).toFuture()
                .whenComplete((img, err) -> {
                    if (img != null && !img.getT1().isEmpty()) {
                        LW.wrap(() -> {
                            Path p;
                            String name = img.getT1().substring(img.getT1().lastIndexOf("/") + 1);
                            if (name.isEmpty()) {
                                p = Paths.get(img.getT1() + ".img");
                            } else {
                                p = Paths.get(name);
                            }
                            //TODO: copy to cdn, rewrite file address url
                            Files.write(p, img.getT2(), StandardOpenOption.CREATE);
                            imageRepo.insert(new ImageInfo(p.getFileName().toString(), p.toAbsolutePath().toString()));
                            LOG.info("File {} written", p.toAbsolutePath());
                        });
                    }
                });
    }
}
