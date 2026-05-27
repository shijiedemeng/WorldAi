package com.aiapi.mcp.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class McpFileClientRequest {
    String requestId;
    String agentCode;
    String operation;
    String path;
    String keyword;
    Integer maxDepth;
    Integer limit;
}
