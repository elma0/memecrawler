package com.elma.memecrawler.integration;
import java.io.IOException;

public interface Streamer {
    String TOPIC = "urls";

    void start() throws IOException;
}
