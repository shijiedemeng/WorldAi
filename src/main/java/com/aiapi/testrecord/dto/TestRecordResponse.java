package com.aiapi.testrecord.dto;

import com.aiapi.common.enums.TestResult;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TestRecordResponse {
    Long id;
    String requirementNo;
    String testType;
    String testTitle;
    String testContent;
    String testerAgentCode;
    String testerName;
    TestResult testResult;
    Integer bugCount;
    String riskDesc;
    String suggestion;
    String attachments;
    LocalDateTime testedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
