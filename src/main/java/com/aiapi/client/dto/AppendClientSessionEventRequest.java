package com.aiapi.client.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppendClientSessionEventRequest {

    @NotBlank
    private String direction;

    @NotBlank
    private String eventType;

    private String payload;
}
