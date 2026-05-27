package com.aiapi.mcp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class McpFileRequest {
    private String operation;
    private String path;
    private String keyword;
    private Integer maxDepth;
    private Integer limit;
}
