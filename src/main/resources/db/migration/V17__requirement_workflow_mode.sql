ALTER TABLE requirement_info
    ADD COLUMN execution_mode VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN workflow_node_mode VARCHAR(32) NOT NULL DEFAULT 'SERIAL',
    ADD COLUMN result_extractable_flag BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_requirement_workflow_ready
    ON requirement_info (root_requirement_no, requirement_type, execution_mode, status, sort_no);
