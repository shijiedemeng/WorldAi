package com.aiapi.markdown.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MarkdownDocumentPageResponse {
    List<MarkdownDocumentResponse> items;
    int page;
    int pageSize;
    long total;
    int totalPages;
}
