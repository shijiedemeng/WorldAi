CREATE TABLE markdown_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    document_status VARCHAR(64) NOT NULL,
    parent_id VARCHAR(128),
    refs TEXT,
    content LONGTEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_markdown_document_id (document_id),
    KEY idx_markdown_document_parent (parent_id, title),
    KEY idx_markdown_document_type_status (document_type, document_status, updated_at),
    KEY idx_markdown_document_title (title)
);
