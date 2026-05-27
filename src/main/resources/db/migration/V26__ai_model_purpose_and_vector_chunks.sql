ALTER TABLE ai_model_setting
    ADD COLUMN model_purpose VARCHAR(32) NOT NULL DEFAULT 'LANGUAGE' AFTER model_name,
    ADD COLUMN vector_chunk_size INT NOT NULL DEFAULT 500 AFTER model_purpose,
    ADD COLUMN vector_chunk_overlap INT NOT NULL DEFAULT 100 AFTER vector_chunk_size;

UPDATE ai_model_setting
SET model_purpose = CASE WHEN support_image_flag = TRUE THEN 'IMAGE' ELSE 'LANGUAGE' END
WHERE model_purpose IS NULL OR model_purpose = 'LANGUAGE';

UPDATE ai_model_setting
SET model_purpose = 'VECTOR'
WHERE LOWER(setting_key) LIKE '%embedding%'
   OR LOWER(setting_key) LIKE '%embed%'
   OR LOWER(model_name) LIKE '%embedding%'
   OR LOWER(model_name) LIKE '%embed%'
   OR LOWER(model_name) LIKE '%bge%'
   OR LOWER(model_name) LIKE '%gte%'
   OR LOWER(model_name) LIKE '%m3e%';

CREATE INDEX idx_ai_model_setting_purpose ON ai_model_setting (model_purpose, enabled_flag, updated_at);

ALTER TABLE project_knowledge_item
    ADD COLUMN vector_chunk_count INT NOT NULL DEFAULT 0 AFTER vector_id;
