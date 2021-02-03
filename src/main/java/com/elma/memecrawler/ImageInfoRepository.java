package com.elma.memecrawler;

import java.util.UUID;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.elma.memecrawler.integration.model.ImageInfo;

@Repository
public interface ImageInfoRepository extends ReactiveMongoRepository<ImageInfo, UUID> {
}
