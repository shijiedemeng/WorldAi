package com.aiapi.knowledge.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProjectKnowledgeSearchResponse {
    String projectCode;
    String query;
    List<Item> items;

    @Value
    @Builder
    public static class Item {
        Long id;
        String title;
        String simpleDesc;
        String content;
        String knowledgeType;
        Double score;
        Double matchScore;
        Double bestChunkScore;
        Integer matchedChunkCount;
    }
}
