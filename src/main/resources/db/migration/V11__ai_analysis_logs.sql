CREATE TABLE ai_analysis_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_type VARCHAR(64) NOT NULL,
    business_no VARCHAR(128) NOT NULL,
    project_code VARCHAR(64),
    ai_setting_key VARCHAR(64),
    model_name VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    request_payload LONGTEXT,
    response_payload LONGTEXT,
    error_message TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_ai_analysis_log_source_time (source_type, created_at),
    KEY idx_ai_analysis_log_project_time (project_code, created_at),
    KEY idx_ai_analysis_log_business (business_no, created_at),
    KEY idx_ai_analysis_log_status_time (status, created_at)
);
