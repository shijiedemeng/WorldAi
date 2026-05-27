ALTER TABLE agent_info
    ADD COLUMN skill_codes TEXT;

CREATE TABLE skill_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    skill_code VARCHAR(128) NOT NULL,
    skill_name VARCHAR(128) NOT NULL,
    skill_desc TEXT,
    content_text LONGTEXT,
    archive_file_name VARCHAR(255),
    archive_content_type VARCHAR(128),
    archive_size BIGINT,
    archive_content LONGBLOB,
    enabled_flag BOOLEAN NOT NULL DEFAULT TRUE,
    last_archive_upload_time DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_skill_code (skill_code)
);
