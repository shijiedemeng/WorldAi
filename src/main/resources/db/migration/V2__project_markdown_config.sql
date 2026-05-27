ALTER TABLE project_info
    ADD COLUMN markdown_sync_mode VARCHAR(32) NOT NULL DEFAULT 'CANCEL';

CREATE TABLE project_markdown_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_code VARCHAR(64) NOT NULL,
    file_type VARCHAR(16) NOT NULL,
    base_key VARCHAR(32),
    file_path VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_markdown_path (project_code, file_path),
    UNIQUE KEY uk_project_markdown_base (project_code, base_key),
    KEY idx_project_markdown_project_type (project_code, file_type)
);
