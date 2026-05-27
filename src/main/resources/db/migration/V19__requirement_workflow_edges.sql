CREATE TABLE requirement_workflow_edge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    root_requirement_no VARCHAR(64) NOT NULL,
    from_requirement_no VARCHAR(64) NOT NULL,
    to_requirement_no VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_requirement_workflow_edge (root_requirement_no, from_requirement_no, to_requirement_no),
    KEY idx_requirement_workflow_edge_from (root_requirement_no, from_requirement_no),
    KEY idx_requirement_workflow_edge_to (root_requirement_no, to_requirement_no)
);
