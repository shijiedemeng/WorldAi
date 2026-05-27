ALTER TABLE requirement_module_info
    ADD COLUMN project_knowledge_search_enabled_flag BOOLEAN NOT NULL DEFAULT FALSE AFTER document_ids,
    ADD COLUMN project_knowledge_search_limit INT NULL AFTER project_knowledge_search_enabled_flag,
    ADD COLUMN project_knowledge_search_min_score DOUBLE NULL AFTER project_knowledge_search_limit;
