ALTER TABLE project_info
    ADD COLUMN base_markdown_sync_mode VARCHAR(32) NOT NULL DEFAULT 'INDEPENDENT',
    ADD COLUMN base_markdown_allow_client_upload BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE project_markdown_file
    ADD COLUMN version_no INT NOT NULL DEFAULT 1,
    ADD COLUMN last_sync_source VARCHAR(32);

CREATE TABLE project_markdown_file_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_code VARCHAR(64) NOT NULL,
    agent_role VARCHAR(32),
    base_key VARCHAR(32) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    version_no INT NOT NULL,
    change_source VARCHAR(32) NOT NULL,
    change_desc VARCHAR(512),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_project_md_history_file (project_code, agent_role, base_key, version_no),
    KEY idx_project_md_history_created (project_code, created_at)
);

ALTER TABLE agent_info
    ADD COLUMN agent_engine_type VARCHAR(32) NOT NULL DEFAULT 'QODER';
