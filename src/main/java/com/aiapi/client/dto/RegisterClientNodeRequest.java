package com.aiapi.client.dto;

import com.aiapi.common.enums.ClientNodeStatus;
import com.aiapi.common.enums.ClientOsType;
import com.aiapi.common.enums.ConnectionProtocol;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterClientNodeRequest {

    @NotBlank
    private String clientCode;

    @NotBlank
    private String clientName;

    @NotNull
    private ClientOsType osType;

    private String hostName;
    private String ipAddress;
    private ConnectionProtocol connectionProtocol;
    private String connectionSessionId;
    private ClientNodeStatus status;
    private String supportedAgentTypes;
    private String appVersion;
    private Boolean mcpEnabled;
    private String mcpServerUrl;

    @Valid
    private List<ClientAgentRuntimeRequest> runtimes;

    @Valid
    private List<ClientControllableAgentRequest> agents;
}
