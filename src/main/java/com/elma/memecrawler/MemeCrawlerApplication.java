package com.elma.memecrawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@ComponentScans({
        @ComponentScan(value = "com.elma.memecrawler"),
        @ComponentScan(value = "com.elma.memecrawler.configuration")
})
@EnableMongoRepositories
@EnableScheduling
public class MemeCrawlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemeCrawlerApplication.class, args);
    }

}
