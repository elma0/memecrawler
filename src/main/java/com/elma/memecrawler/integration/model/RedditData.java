package com.elma.memecrawler.integration.model;

import java.util.List;

public record RedditData(float downs, String id, String url, List<RedditContent> children, String after) {
}
