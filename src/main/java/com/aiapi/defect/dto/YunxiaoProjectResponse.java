package com.aiapi.defect.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class YunxiaoProjectResponse {
    String id;
    String name;
    String description;
    String customCode;
    String scope;
    String creatorId;
    String creatorName;
    String modifierId;
    String modifierName;
    String statusId;
    String statusName;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
