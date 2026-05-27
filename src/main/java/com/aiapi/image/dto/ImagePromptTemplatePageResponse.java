package com.aiapi.image.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ImagePromptTemplatePageResponse {
    List<ImagePromptTemplateResponse> items;
    int page;
    int pageSize;
    long total;
    int totalPages;
}
