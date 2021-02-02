package com.elma.memecrawler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

import com.elma.memecrawler.common.LW;
import com.elma.memecrawler.integration.model.ImageInfo;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public class ImageDownloadingService implements ImageConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(ImageDownloadingService.class);
    public final HttpClient.ResponseReceiver<?> httpClient;
    public final ImageInfoRepository imageRepo;

    public ImageDownloadingService(
            HttpClient.ResponseReceiver<?> httpClient,
            ImageInfoRepository imageRepo
    ) {
        this.httpClient = httpClient;
        this.imageRepo = imageRepo;
    }

    @KafkaListener(topics = "urls", groupId = "0")
    public void onImage(String imageUrl) {
        httpClient.uri(imageUrl).responseSingle((response, body) -> {
            String contentType = response.responseHeaders().get("Content-Type");
            if (contentType != null && contentType.startsWith("image")) {
                return Mono.defer(body::asByteArray);
            }
            return Mono.empty();
        }).toFuture().whenComplete((img, err) -> {
            if (err != null) {
                LOG.error("Error requesting file", err);
                return;
            }
            if (img != null && img.length > 0) {
                int pos = imageUrl.lastIndexOf("/");
                if (pos > 0) {
                    Path p = Paths.get("images", imageUrl.substring(pos + 1));
                    LW.wrap(() -> Files.write(p, img, StandardOpenOption.CREATE));
                    Mono<ImageInfo> mono = imageRepo.insert(new ImageInfo(p.getFileName().toString()));
                    mono.doOnError(t -> LOG.error("Error saving image", t));
                    LOG.trace("File {} written", p.toAbsolutePath());
                }
            }
        });
    }
}
