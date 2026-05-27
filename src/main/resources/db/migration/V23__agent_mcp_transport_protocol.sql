ALTER TABLE agent_info
    ADD COLUMN mcp_transport_protocol VARCHAR(32) NOT NULL DEFAULT 'SSE' AFTER endpoint_url;
