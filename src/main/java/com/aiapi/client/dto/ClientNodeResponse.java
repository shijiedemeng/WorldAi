package com.aiapi.client.dto;

import com.aiapi.common.enums.ClientNodeStatus;
import com.aiapi.common.enums.ClientOsType;
import com.aiapi.common.enums.ConnectionProtocol;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ClientNodeResponse {
    Long id;
    String clientCode;
    String clientName;
    ClientOsType osType;
    String hostName;
    String ipAddress;
    ConnectionProtocol connectionProtocol;
    String connectionSessionId;
    ClientNodeStatus status;
    LocalDateTime lastHeartbeatTime;
    String supportedAgentTypes;
    String appVersion;
    Boolean mcpEnabled;
    String mcpServerUrl;
    List<ClientAgentRuntimeResponse> runtimes;
    List<ClientControllableAgentResponse> agents;
    List<ClientUnlinkedAgentResponse> unlinkedAgents;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
