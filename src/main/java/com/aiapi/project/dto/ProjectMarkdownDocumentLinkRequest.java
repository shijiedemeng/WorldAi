package com.aiapi.project.dto;

import com.aiapi.common.enums.ProjectDocumentUsage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectMarkdownDocumentLinkRequest {

    @NotNull
    private ProjectDocumentUsage usageType;

    @NotBlank
    private String documentId;
}
