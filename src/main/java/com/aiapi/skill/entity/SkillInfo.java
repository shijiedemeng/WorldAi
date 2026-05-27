package com.aiapi.skill.entity;

import com.aiapi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "skill_info")
public class SkillInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_code", nullable = false, unique = true, length = 128)
    private String skillCode;

    @Column(name = "skill_name", nullable = false, length = 128)
    private String skillName;

    @Column(name = "skill_desc")
    private String skillDesc;

    @Lob
    @Column(name = "content_text", columnDefinition = "LONGTEXT")
    private String contentText;

    @Column(name = "archive_file_name", length = 255)
    private String archiveFileName;

    @Column(name = "archive_content_type", length = 128)
    private String archiveContentType;

    @Column(name = "archive_size")
    private Long archiveSize;

    @Lob
    @Column(name = "archive_content", columnDefinition = "LONGBLOB")
    private byte[] archiveContent;

    @Column(name = "enabled_flag", nullable = false)
    private Boolean enabledFlag;

    @Column(name = "last_archive_upload_time")
    private LocalDateTime lastArchiveUploadTime;
}
