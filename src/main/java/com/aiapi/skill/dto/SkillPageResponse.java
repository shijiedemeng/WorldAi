package com.aiapi.skill.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SkillPageResponse {
    List<SkillResponse> items;
    int page;
    int pageSize;
    long total;
    int totalPages;
}
