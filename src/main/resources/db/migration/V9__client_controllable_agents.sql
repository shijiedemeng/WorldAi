CREATE TABLE client_controllable_agent (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    client_code VARCHAR(64) NOT NULL,
    agent_code VARCHAR(64) NOT NULL,
    enabled_flag BOOLEAN NOT NULL DEFAULT TRUE,
    workspace_dir VARCHAR(512),
    worker_command VARCHAR(512),
    last_seen_time DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_client_controllable_agent (client_code, agent_code),
    KEY idx_client_controllable_agent_client (client_code, enabled_flag, updated_at),
    KEY idx_client_controllable_agent_agent (agent_code, updated_at)
);
