DELETE legacy
FROM project_markdown_document_link legacy
JOIN project_markdown_document_link current_link
    ON current_link.project_code = legacy.project_code
    AND current_link.document_id = legacy.document_id
    AND current_link.usage_type = 'AGENT_COMMON'
WHERE legacy.usage_type = 'GENERAL_DOCUMENT';

UPDATE project_markdown_document_link
SET usage_type = 'AGENT_COMMON'
WHERE usage_type = 'GENERAL_DOCUMENT';

DELETE legacy
FROM project_markdown_document_link legacy
JOIN project_markdown_document_link current_link
    ON current_link.project_code = legacy.project_code
    AND current_link.document_id = legacy.document_id
    AND current_link.usage_type = 'AGENT_DEVELOPER'
WHERE legacy.usage_type = 'DEVELOPMENT_DOCUMENT';

UPDATE project_markdown_document_link
SET usage_type = 'AGENT_DEVELOPER'
WHERE usage_type = 'DEVELOPMENT_DOCUMENT';

ALTER TABLE requirement_module_info
    ADD COLUMN document_ids VARCHAR(1024) NULL AFTER mcp_file_search_enabled_flag;
