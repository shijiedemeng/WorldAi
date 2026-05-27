CREATE TABLE project_markdown_document_link (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_code VARCHAR(64) NOT NULL,
    usage_type VARCHAR(64) NOT NULL,
    document_id VARCHAR(128) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_md_doc_usage (project_code, usage_type, document_id),
    KEY idx_project_md_doc_project_usage (project_code, usage_type, sort_no),
    KEY idx_project_md_doc_document (document_id)
);
