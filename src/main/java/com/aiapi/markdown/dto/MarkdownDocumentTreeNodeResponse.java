package com.aiapi.markdown.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MarkdownDocumentTreeNodeResponse {
    String documentId;
    String title;
    String nodeType;
    String type;
    String status;
    String parentId;
    List<String> refs;
    List<MarkdownDocumentTreeNodeResponse> children;
}
