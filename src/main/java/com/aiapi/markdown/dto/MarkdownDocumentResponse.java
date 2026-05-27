package com.aiapi.markdown.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MarkdownDocumentResponse {
    Long id;
    String documentId;
    String title;
    String nodeType;
    String type;
    String status;
    String parentId;
    List<String> refs;
    String content;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
