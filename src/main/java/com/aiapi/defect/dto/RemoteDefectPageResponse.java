package com.aiapi.defect.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RemoteDefectPageResponse {
    List<RemoteDefectResponse> items;
    int page;
    int pageSize;
    long total;
    int totalPages;
}
