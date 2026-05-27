package com.aiapi.session.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.SessionBindingType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "requirement_session_binding")
public class RequirementSessionBinding extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requirement_no", nullable = false, length = 64)
    private String requirementNo;

    @Column(name = "root_requirement_no", nullable = false, length = 64)
    private String rootRequirementNo;

    @Column(name = "session_code", nullable = false, length = 64)
    private String sessionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "binding_type", nullable = false, length = 32)
    private SessionBindingType bindingType;

    @Column(name = "is_primary_flag", nullable = false)
    private Boolean primaryFlag;

    @Column(name = "remark", length = 512)
    private String remark;
}
