package com.aiapi.defect.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.DefectPlatformType;
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
@Table(name = "defect_sync_account")
public class DefectSyncAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_code", nullable = false, unique = true, length = 64)
    private String accountCode;

    @Column(name = "account_name", nullable = false, length = 128)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_type", nullable = false, length = 32)
    private DefectPlatformType platformType;

    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    @Column(name = "username", length = 128)
    private String username;

    @Column(name = "password_value", length = 512)
    private String passwordValue;

    @Column(name = "access_token", length = 512)
    private String accessToken;

    @Column(name = "enabled_flag", nullable = false)
    private Boolean enabledFlag;

    @Column(name = "extra_config", columnDefinition = "TEXT")
    private String extraConfig;
}
