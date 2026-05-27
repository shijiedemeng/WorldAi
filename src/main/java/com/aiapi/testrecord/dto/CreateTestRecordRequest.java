package com.aiapi.testrecord.dto;

import com.aiapi.common.enums.TestResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTestRecordRequest {

    @NotBlank
    private String testType;

    @NotBlank
    private String testTitle;

    private String testContent;
    private String testerAgentCode;
    private String testerName;

    @NotNull
    private TestResult testResult;

    @NotNull
    private Integer bugCount;

    private String riskDesc;
    private String suggestion;
    private String attachments;
    private LocalDateTime testedAt;
}
