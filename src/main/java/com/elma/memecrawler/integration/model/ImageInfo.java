package com.elma.memecrawler.integration.model;

import java.util.UUID;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "image_info")
public record ImageInfo(UUID id, String name, String url) {
    public ImageInfo(String name, String url) {
        this(null, name, url);
    }
}
