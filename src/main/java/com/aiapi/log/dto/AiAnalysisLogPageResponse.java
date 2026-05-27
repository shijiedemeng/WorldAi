package com.aiapi.log.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AiAnalysisLogPageResponse {
    List<AiAnalysisLogResponse> items;
    int page;
    int pageSize;
    long total;
    int totalPages;
}
