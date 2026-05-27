package com.aiapi.knowledge.dto;

import com.aiapi.common.enums.ProjectKnowledgeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProjectKnowledgeRequest {

    @NotNull
    private ProjectKnowledgeType knowledgeType;

    @NotBlank
    private String title;

    private String simpleDesc;
    private String detailContent;
    private String organizedContent;
    private String confirmedContent;
    private String aiSettingKey;
    private String embeddingSettingKey;
    private String documentIds;
    private Long selectedSimilarKnowledgeId;
}
