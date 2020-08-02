package com.elma.memecrawler;

import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.elma.memecrawler.integration.model.ImageInfo;

@Repository
public interface ImageMetaRepository extends MongoRepository<ImageInfo, UUID> {
}
