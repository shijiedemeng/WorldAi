CREATE TABLE project_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    project_desc TEXT,
    business_goal TEXT,
    tech_stack TEXT,
    repository_url VARCHAR(512),
    owner_agent_code VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_code (project_code)
);

CREATE TABLE requirement_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    requirement_no VARCHAR(64) NOT NULL,
    project_code VARCHAR(64) NOT NULL,
    title VARCHAR(256) NOT NULL,
    requirement_desc TEXT,
    priority VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    source VARCHAR(128),
    main_agent_code VARCHAR(64),
    current_stage VARCHAR(64),
    expected_deadline DATETIME,
    created_by VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_requirement_no (requirement_no),
    KEY idx_requirement_project_status (project_code, status)
);

CREATE TABLE agent_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_code VARCHAR(64) NOT NULL,
    agent_name VARCHAR(128) NOT NULL,
    agent_role VARCHAR(32) NOT NULL,
    agent_desc TEXT,
    capability_tags TEXT,
    supported_link_types TEXT,
    callback_mode VARCHAR(32),
    endpoint_url VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    last_heartbeat_time DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_code (agent_code)
);

CREATE TABLE requirement_dev_link (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    requirement_no VARCHAR(64) NOT NULL,
    link_type VARCHAR(32) NOT NULL,
    task_title VARCHAR(256) NOT NULL,
    task_desc TEXT,
    agent_code VARCHAR(64) NOT NULL,
    developer_name VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    result_summary TEXT,
    deliverable_path VARCHAR(512),
    depends_on_link_id BIGINT,
    started_at DATETIME,
    finished_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_link_requirement_agent_status (requirement_no, agent_code, status, link_type),
    KEY idx_link_depends (depends_on_link_id)
);

CREATE TABLE requirement_test_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    requirement_no VARCHAR(64) NOT NULL,
    test_type VARCHAR(64) NOT NULL,
    test_title VARCHAR(256) NOT NULL,
    test_content TEXT,
    tester_agent_code VARCHAR(64),
    tester_name VARCHAR(128),
    test_result VARCHAR(32) NOT NULL,
    bug_count INT NOT NULL DEFAULT 0,
    risk_desc TEXT,
    suggestion TEXT,
    attachments TEXT,
    tested_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_test_requirement_result (requirement_no, test_result)
);
