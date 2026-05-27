package com.aiapi.markdown.dto;

import lombok.Data;

@Data
public class SaveMarkdownDocumentRequest {

    private String documentId;
    private String title;
    private String nodeType;
    private String type;
    private String status;
    private String parentId;
    private java.util.List<String> refs;
    private String body;
    private String content;
}
