package com.aiapi.mcp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class McpFileEntry {
    private String path;
    private String name;
    private String type;
    private Long size;
    private Integer depth;
    private String preview;
}
