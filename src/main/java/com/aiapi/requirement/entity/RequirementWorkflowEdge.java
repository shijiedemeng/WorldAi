package com.aiapi.requirement.entity;

import com.aiapi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "requirement_workflow_edge")
public class RequirementWorkflowEdge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "root_requirement_no", nullable = false, length = 64)
    private String rootRequirementNo;

    @Column(name = "from_requirement_no", nullable = false, length = 64)
    private String fromRequirementNo;

    @Column(name = "to_requirement_no", nullable = false, length = 64)
    private String toRequirementNo;
}
