package com.aiapi.defect.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.DefectPlatformType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "defect_sync_project")
public class DefectSyncProject extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sync_code", nullable = false, unique = true, length = 64)
    private String syncCode;

    @Column(name = "project_code", nullable = false, length = 64)
    private String projectCode;

    @Column(name = "account_code", nullable = false, length = 64)
    private String accountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_type", nullable = false, length = 32)
    private DefectPlatformType platformType;

    @Column(name = "external_project_key", nullable = false, length = 128)
    private String externalProjectKey;

    @Column(name = "external_project_name", nullable = false, length = 255)
    private String externalProjectName;

}
