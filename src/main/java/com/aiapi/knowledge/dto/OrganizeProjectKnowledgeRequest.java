package com.aiapi.knowledge.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizeProjectKnowledgeRequest {

    private String aiSettingKey;
    private String documentIds;
    private Long selectedSimilarKnowledgeId;
    private String extraPrompt;
}
