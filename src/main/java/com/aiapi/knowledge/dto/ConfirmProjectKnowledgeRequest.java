package com.aiapi.knowledge.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmProjectKnowledgeRequest {

    private String confirmedContent;
    private String embeddingSettingKey;
}
