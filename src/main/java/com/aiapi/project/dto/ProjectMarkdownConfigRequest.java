package com.aiapi.project.dto;

import com.aiapi.common.enums.ProjectMarkdownSyncMode;
import com.aiapi.common.enums.ProjectMarkdownBaseSyncMode;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectMarkdownConfigRequest {

    @NotNull
    private ProjectMarkdownSyncMode markdownSyncMode;

    private ProjectMarkdownBaseSyncMode baseMarkdownSyncMode;

    private Boolean baseMarkdownAllowClientUpload;

    private List<ProjectMarkdownFileRequest> markdownFiles;

    private List<ProjectMarkdownDocumentLinkRequest> documentLinks;
}
