package com.aiapi.common.enums;

public enum ProjectDocumentUsage {
    REQUIREMENT_AI_ANALYSIS,
    DEFECT_AI_ANALYSIS,
    AGENT_COMMON,
    AGENT_MAIN,
    AGENT_DEVELOPER,
    AGENT_TESTER,
    AGENT_OPS,
    AGENT_REVIEWER,

    @Deprecated
    GENERAL_DOCUMENT,
    @Deprecated
    DEVELOPMENT_DOCUMENT
}
