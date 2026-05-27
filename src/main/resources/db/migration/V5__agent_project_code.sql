ALTER TABLE agent_info
    ADD COLUMN project_code VARCHAR(64) NULL AFTER agent_name;

CREATE INDEX idx_agent_project_status ON agent_info (project_code, status);
