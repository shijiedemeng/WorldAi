package com.aiapi.defect.integration;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DefectPlatformBugPage {
    List<DefectPlatformBug> items;
    int page;
    int pageSize;
    long total;
    int totalPages;
}
