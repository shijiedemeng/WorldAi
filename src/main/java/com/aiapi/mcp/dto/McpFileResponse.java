package com.aiapi.mcp.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class McpFileResponse {
    private String requestId;
    private String agentCode;
    private String clientCode;
    private Boolean enabled;
    private String operation;
    private String basePath;
    private String path;
    private List<McpFileEntry> entries;
    private String content;
    private List<McpFileEntry> matches;
    private String errorMessage;
}
