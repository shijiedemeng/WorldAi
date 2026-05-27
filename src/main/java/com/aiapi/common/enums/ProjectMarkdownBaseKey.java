package com.aiapi.common.enums;

public enum ProjectMarkdownBaseKey {
    AGENTS("AGENTS.md"),
    SOUL("SOUL.md"),
    USER("USER.md"),
    MEMORY("MEMORY.md"),
    LEARNINGS(".learnings/LEARNINGS.md");

    private final String defaultPath;

    ProjectMarkdownBaseKey(String defaultPath) {
        this.defaultPath = defaultPath;
    }

    public String getDefaultPath() {
        return defaultPath;
    }
}
