package com.aiapi.knowledge.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProjectKnowledgePageResponse {
    List<ProjectKnowledgeResponse> items;
    int page;
    int pageSize;
    long total;
    int totalPages;
}
