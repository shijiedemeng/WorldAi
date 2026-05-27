package com.aiapi.project.dto;

import com.aiapi.common.enums.ProjectMarkdownSyncMode;
import com.aiapi.common.enums.ProjectMarkdownBaseSyncMode;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProjectMarkdownConfigResponse {
    String projectCode;
    ProjectMarkdownSyncMode markdownSyncMode;
    ProjectMarkdownBaseSyncMode baseMarkdownSyncMode;
    Boolean baseMarkdownAllowClientUpload;
    List<ProjectMarkdownFileResponse> markdownFiles;
    List<ProjectMarkdownDocumentLinkResponse> documentLinks;
}
