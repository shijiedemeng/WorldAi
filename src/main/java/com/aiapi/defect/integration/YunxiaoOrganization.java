package com.aiapi.defect.integration;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class YunxiaoOrganization {
    String id;
    String name;
    String description;
    String creatorId;
    String defaultRole;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
