package com.aiapi.knowledge.dto;

import com.aiapi.common.enums.ProjectKnowledgeType;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchProjectKnowledgeRequest {

    @NotBlank
    private String projectCode;

    @NotBlank
    private String query;

    private ProjectKnowledgeType knowledgeType;
    private String embeddingSettingKey;
    private Integer limit;
    private Double minMatchScore;
}
