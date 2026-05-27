ALTER TABLE markdown_document
    ADD COLUMN node_type VARCHAR(32) NOT NULL DEFAULT 'DOCUMENT';

CREATE INDEX idx_markdown_document_node_type ON markdown_document (node_type, parent_id, title);
