package com.elma.memecrawler.integration;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class StreamerService {
    private final List<Streamer> streamers;

    @Autowired
    public StreamerService(List<Streamer> streamers) {
        this.streamers = streamers;
    }

    @Scheduled(initialDelay = 1000L, fixedDelay = 86400000L)
    public void downloadContents() throws IOException {
        for (Streamer streamer : streamers) {
            streamer.start();
        }
    }
}
