ALTER TABLE project_markdown_file
    ADD COLUMN agent_role VARCHAR(32);

ALTER TABLE project_markdown_file
    DROP INDEX uk_project_markdown_path;

ALTER TABLE project_markdown_file
    DROP INDEX uk_project_markdown_base;

CREATE INDEX idx_project_markdown_project_role_type
    ON project_markdown_file (project_code, agent_role, file_type, updated_at);

CREATE INDEX idx_project_markdown_project_role_path
    ON project_markdown_file (project_code, agent_role, file_path);

ALTER TABLE requirement_info
    ADD COLUMN execution_steps TEXT;
