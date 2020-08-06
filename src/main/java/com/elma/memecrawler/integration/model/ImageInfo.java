package com.elma.memecrawler.integration.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Document(collection = "image_info")
public class ImageInfo {
    @Id
    @Field("_id")
    @JsonIgnore
    private String id;
    private String name;

    public ImageInfo(String name) {
        this.name = name;
    }

    public ImageInfo(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
