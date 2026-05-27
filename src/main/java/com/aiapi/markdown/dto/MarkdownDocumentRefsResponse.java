package com.aiapi.markdown.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MarkdownDocumentRefsResponse {
    String documentId;
    List<String> refs;
    List<String> missingRefs;
    List<MarkdownDocumentResponse> outgoingDocuments;
    List<MarkdownDocumentResponse> incomingDocuments;
}
