package com.elma.memecrawler.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import com.elma.memecrawler.ImageConsumer;
import com.elma.memecrawler.ImageDownloadingService;
import com.elma.memecrawler.ImageInfoRepository;
import com.elma.memecrawler.integration.RedditStreamer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.reactivestreams.client.MongoClient;

import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;

@Configuration
public class AppConfiguration {

    String TOPIC = "urls";

    @Bean
    public ReactiveMongoTemplate reactiveMongoTemplate(MongoClient mongoClient) {
        return new ReactiveMongoTemplate(mongoClient, "test");
    }

    @Bean
    @Qualifier("http.client")
    public HttpClient.ResponseReceiver<?> httpClient() {
        return HttpClient.create()
                .protocol(HttpProtocol.HTTP11)
                .port(443)
                .get();
    }

    @Bean
    public ImageDownloadingService downloadingService(ImageInfoRepository imageRepo) {
        return new ImageDownloadingService(httpClient(), imageRepo);
    }

    @Bean
    public RedditStreamer redditStreamer(
            @Value("${reddit.url}") String url,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper om
    ) {
        return new RedditStreamer(httpClient(), u -> kafkaTemplate.send(TOPIC, u), url, om);
    }

    @Bean
    @Qualifier("downloadService")
    public ImageConsumer diskAndMongo(ImageDownloadingService imageDownloadingService) {
        return imageDownloadingService;
    }
}
