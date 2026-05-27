ALTER TABLE project_info
    ADD COLUMN file_search_mcp_agent_codes VARCHAR(512) NULL AFTER owner_agent_code;

ALTER TABLE requirement_info
    ADD COLUMN auto_execute_flag BOOLEAN NOT NULL DEFAULT FALSE AFTER execution_steps,
    ADD COLUMN file_search_mcp_agent_codes VARCHAR(512) NULL AFTER auto_execute_flag;

ALTER TABLE requirement_module_info
    ADD COLUMN mcp_file_search_enabled_flag BOOLEAN NOT NULL DEFAULT FALSE AFTER execution_steps;
