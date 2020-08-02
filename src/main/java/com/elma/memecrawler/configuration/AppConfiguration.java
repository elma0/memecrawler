package com.elma.memecrawler.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;

@Configuration
public class AppConfiguration {

    @Bean
    @Qualifier("http.client")
    public HttpClient.ResponseReceiver<?> httpClient() {
        return HttpClient.create()
                .protocol(HttpProtocol.HTTP11)
                .port(443)
                .get();
    }
}
