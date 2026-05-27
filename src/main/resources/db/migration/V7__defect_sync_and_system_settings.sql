CREATE TABLE ai_model_setting (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    setting_key VARCHAR(64) NOT NULL,
    provider_name VARCHAR(64) NOT NULL,
    base_url VARCHAR(512),
    api_key VARCHAR(512),
    model_name VARCHAR(128) NOT NULL,
    support_image_flag BOOLEAN NOT NULL DEFAULT FALSE,
    prompt_template TEXT,
    enabled_flag BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_model_setting_key (setting_key),
    KEY idx_ai_model_setting_enabled (enabled_flag, updated_at)
);

CREATE TABLE defect_sync_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_code VARCHAR(64) NOT NULL,
    account_name VARCHAR(128) NOT NULL,
    platform_type VARCHAR(32) NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    username VARCHAR(128),
    password_value VARCHAR(512),
    access_token VARCHAR(512),
    enabled_flag BOOLEAN NOT NULL DEFAULT TRUE,
    extra_config TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_defect_sync_account_code (account_code),
    KEY idx_defect_sync_account_platform_enabled (platform_type, enabled_flag)
);

CREATE TABLE defect_sync_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sync_code VARCHAR(64) NOT NULL,
    project_code VARCHAR(64) NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    platform_type VARCHAR(32) NOT NULL,
    external_project_key VARCHAR(128) NOT NULL,
    external_project_name VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_defect_sync_code (sync_code),
    UNIQUE KEY uk_defect_sync_project_binding (project_code, account_code, external_project_key),
    KEY idx_defect_sync_project_project (project_code, updated_at),
    KEY idx_defect_sync_project_account (account_code, updated_at)
);

CREATE TABLE defect_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sync_code VARCHAR(64) NOT NULL,
    project_code VARCHAR(64) NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    platform_type VARCHAR(32) NOT NULL,
    external_defect_id VARCHAR(128) NOT NULL,
    external_defect_key VARCHAR(128),
    title VARCHAR(255) NOT NULL,
    severity VARCHAR(32),
    defect_status VARCHAR(32),
    defect_type VARCHAR(64),
    assigned_to VARCHAR(128),
    reporter_name VARCHAR(128),
    opened_at DATETIME,
    updated_at_remote DATETIME,
    has_image_flag BOOLEAN NOT NULL DEFAULT FALSE,
    tags VARCHAR(512),
    summary TEXT,
    description_text TEXT,
    raw_payload TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_defect_record_unique (sync_code, external_defect_id),
    KEY idx_defect_record_project_status (project_code, defect_status, updated_at),
    KEY idx_defect_record_sync_time (sync_code, updated_at)
);

CREATE TABLE defect_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    defect_record_id BIGINT NOT NULL,
    external_comment_id VARCHAR(128),
    author_name VARCHAR(128),
    comment_content TEXT,
    commented_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_defect_comment_record_time (defect_record_id, commented_at),
    KEY idx_defect_comment_external (external_comment_id)
);

CREATE TABLE defect_analysis_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    analysis_no VARCHAR(64) NOT NULL,
    project_code VARCHAR(64) NOT NULL,
    defect_record_id BIGINT NOT NULL,
    ai_setting_key VARCHAR(64),
    agent_scope TEXT,
    prompt_text TEXT,
    edited_summary TEXT,
    analysis_result TEXT,
    next_requirement_no VARCHAR(64),
    push_status VARCHAR(32) NOT NULL,
    pushed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_defect_analysis_no (analysis_no),
    KEY idx_defect_analysis_project_status (project_code, push_status, created_at),
    KEY idx_defect_analysis_record (defect_record_id, created_at)
);
