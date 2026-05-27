package com.aiapi.defect.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DefectRemoteQueryRequest {
    private Integer page;
    private Integer pageSize;
    private String keyword;
    private String status;
    private String severity;
    private String assignedTo;
    private String reporterName;
    private String tag;
    private String orderBy;
    private String sort;
}
