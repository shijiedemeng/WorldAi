ALTER TABLE requirement_info
    ADD COLUMN requirement_type VARCHAR(16) NOT NULL DEFAULT 'MASTER',
    ADD COLUMN parent_requirement_no VARCHAR(64),
    ADD COLUMN root_requirement_no VARCHAR(64) NOT NULL DEFAULT 'TEMP_ROOT',
    ADD COLUMN sort_no INT NOT NULL DEFAULT 0,
    ADD COLUMN session_strategy VARCHAR(32),
    ADD COLUMN preferred_session_code VARCHAR(64);

UPDATE requirement_info
SET root_requirement_no = requirement_no
WHERE root_requirement_no = 'TEMP_ROOT';

ALTER TABLE project_markdown_file
    ADD COLUMN sync_enabled_flag BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN display_order INT NOT NULL DEFAULT 0,
    ADD COLUMN content_hash VARCHAR(64);

CREATE INDEX idx_requirement_root_sort ON requirement_info (root_requirement_no, sort_no, status);
CREATE INDEX idx_requirement_parent_sort ON requirement_info (parent_requirement_no, sort_no);
CREATE INDEX idx_requirement_project_root ON requirement_info (project_code, root_requirement_no);

CREATE TABLE project_markdown_sync_conflict (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_code VARCHAR(64) NOT NULL,
    client_code VARCHAR(64) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    sync_mode VARCHAR(32) NOT NULL,
    server_content_hash VARCHAR(64),
    local_content_hash VARCHAR(64),
    conflict_reason VARCHAR(512),
    resolved_flag BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_md_conflict_project_client (project_code, client_code, created_at),
    KEY idx_md_conflict_resolved (resolved_flag, created_at)
);

CREATE TABLE agent_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_code VARCHAR(64) NOT NULL,
    session_name VARCHAR(128),
    session_type VARCHAR(32) NOT NULL,
    project_code VARCHAR(64) NOT NULL,
    requirement_no VARCHAR(64),
    root_requirement_no VARCHAR(64),
    agent_code VARCHAR(64) NOT NULL,
    client_code VARCHAR(64) NOT NULL,
    external_session_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    reusable_flag BOOLEAN NOT NULL DEFAULT TRUE,
    last_active_time DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_session_code (session_code),
    KEY idx_session_agent_reusable (agent_code, reusable_flag, status, last_active_time),
    KEY idx_session_client_status (client_code, status, last_active_time),
    KEY idx_session_root_req (root_requirement_no, status, last_active_time),
    KEY idx_session_project_agent (project_code, agent_code, last_active_time)
);

CREATE TABLE requirement_session_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    requirement_no VARCHAR(64) NOT NULL,
    root_requirement_no VARCHAR(64) NOT NULL,
    session_code VARCHAR(64) NOT NULL,
    binding_type VARCHAR(32) NOT NULL,
    is_primary_flag BOOLEAN NOT NULL DEFAULT FALSE,
    remark VARCHAR(512),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_req_session_req (requirement_no, is_primary_flag, created_at),
    KEY idx_req_session_session (session_code, created_at)
);

CREATE TABLE client_node (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    client_code VARCHAR(64) NOT NULL,
    client_name VARCHAR(128) NOT NULL,
    os_type VARCHAR(32) NOT NULL,
    host_name VARCHAR(128),
    ip_address VARCHAR(64),
    connection_protocol VARCHAR(32) NOT NULL DEFAULT 'WEBSOCKET',
    connection_session_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    last_heartbeat_time DATETIME,
    supported_agent_types TEXT,
    app_version VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_client_node_code (client_code),
    KEY idx_client_status_heartbeat (status, last_heartbeat_time)
);

CREATE TABLE client_agent_runtime (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    client_code VARCHAR(64) NOT NULL,
    agent_type VARCHAR(32) NOT NULL,
    agent_name VARCHAR(128) NOT NULL,
    agent_version VARCHAR(64),
    command_path VARCHAR(512),
    available_flag BOOLEAN NOT NULL DEFAULT TRUE,
    capability_tags TEXT,
    supports_session_reuse BOOLEAN NOT NULL DEFAULT FALSE,
    default_workspace_dir VARCHAR(512),
    last_probe_time DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_runtime_client_available (client_code, available_flag, agent_type),
    KEY idx_runtime_agent_type_available (agent_type, available_flag)
);

CREATE TABLE dispatch_task_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dispatch_no VARCHAR(64) NOT NULL,
    project_code VARCHAR(64) NOT NULL,
    requirement_no VARCHAR(64) NOT NULL,
    root_requirement_no VARCHAR(64),
    link_id BIGINT,
    client_code VARCHAR(64) NOT NULL,
    agent_code VARCHAR(64) NOT NULL,
    session_strategy VARCHAR(32) NOT NULL,
    session_code VARCHAR(64),
    task_payload TEXT NOT NULL,
    dispatch_status VARCHAR(32) NOT NULL,
    need_sync_markdown BOOLEAN NOT NULL DEFAULT FALSE,
    priority VARCHAR(32),
    timeout_seconds INT,
    dispatch_time DATETIME,
    ack_time DATETIME,
    started_at DATETIME,
    finished_at DATETIME,
    result_summary TEXT,
    deliverable_path VARCHAR(512),
    error_message VARCHAR(1024),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dispatch_no (dispatch_no),
    KEY idx_dispatch_req_status (requirement_no, dispatch_status, created_at),
    KEY idx_dispatch_root_status (root_requirement_no, dispatch_status, created_at),
    KEY idx_dispatch_client_status (client_code, dispatch_status, created_at),
    KEY idx_dispatch_agent_status (agent_code, dispatch_status, created_at)
);

CREATE TABLE dispatch_task_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dispatch_no VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_status VARCHAR(32),
    event_message VARCHAR(1024),
    event_payload TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_dispatch_event_no_time (dispatch_no, created_at)
);
