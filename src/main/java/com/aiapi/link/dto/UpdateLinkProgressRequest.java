package com.aiapi.link.dto;

import com.aiapi.common.enums.LinkStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateLinkProgressRequest {

    @NotNull
    private LinkStatus status;

    private String resultSummary;
    private String executionDetails;
    private String deliverablePath;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
