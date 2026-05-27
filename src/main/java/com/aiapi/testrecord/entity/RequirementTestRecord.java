package com.aiapi.testrecord.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.TestResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "requirement_test_record")
public class RequirementTestRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requirement_no", nullable = false, length = 64)
    private String requirementNo;

    @Column(name = "test_type", nullable = false, length = 64)
    private String testType;

    @Column(name = "test_title", nullable = false, length = 256)
    private String testTitle;

    @Column(name = "test_content")
    private String testContent;

    @Column(name = "tester_agent_code", length = 64)
    private String testerAgentCode;

    @Column(name = "tester_name", length = 128)
    private String testerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_result", nullable = false, length = 32)
    private TestResult testResult;

    @Column(name = "bug_count", nullable = false)
    private Integer bugCount;

    @Column(name = "risk_desc")
    private String riskDesc;

    @Column(name = "suggestion")
    private String suggestion;

    @Column(name = "attachments")
    private String attachments;

    @Column(name = "tested_at")
    private LocalDateTime testedAt;
}
