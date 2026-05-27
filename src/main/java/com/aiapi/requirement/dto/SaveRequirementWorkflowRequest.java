package com.aiapi.requirement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveRequirementWorkflowRequest {

    @Valid
    @NotNull
    private List<Edge> edges = new ArrayList<>();

    @Getter
    @Setter
    public static class Edge {
        private String fromRequirementNo;
        private String toRequirementNo;
    }
}
