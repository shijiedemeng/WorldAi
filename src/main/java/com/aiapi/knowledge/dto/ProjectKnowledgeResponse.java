package com.aiapi.knowledge.dto;

import com.aiapi.common.enums.ProjectKnowledgeStatus;
import com.aiapi.common.enums.ProjectKnowledgeType;
import com.aiapi.common.enums.ProjectKnowledgeVectorStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProjectKnowledgeResponse {
    Long id;
    String projectCode;
    ProjectKnowledgeType knowledgeType;
    String title;
    String simpleDesc;
    String detailContent;
    String organizedContent;
    String confirmedContent;
    String sourceRequirementNo;
    String sourceSummary;
    String aiSettingKey;
    String embeddingSettingKey;
    String documentIds;
    Long selectedSimilarKnowledgeId;
    Double selectedSimilarScore;
    ProjectKnowledgeStatus status;
    ProjectKnowledgeVectorStatus vectorStatus;
    Boolean vectorDirtyFlag;
    String vectorCollection;
    String vectorId;
    Integer vectorChunkCount;
    String vectorFilePath;
    LocalDateTime vectorUpdatedAt;
    String errorMessage;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
