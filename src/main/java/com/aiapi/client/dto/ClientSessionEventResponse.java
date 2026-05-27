package com.aiapi.client.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ClientSessionEventResponse {
    String eventId;
    String direction;
    String eventType;
    String payload;
    LocalDateTime createdAt;
}
