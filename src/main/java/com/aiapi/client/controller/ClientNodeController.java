package com.aiapi.client.controller;

import com.aiapi.client.dto.AppendClientSessionEventRequest;
import com.aiapi.client.dto.ClientNodeResponse;
import com.aiapi.client.dto.ClientAgentSessionResponse;
import com.aiapi.client.dto.ClientCommandResponse;
import com.aiapi.client.dto.CompleteClientAgentSessionRequest;
import com.aiapi.client.dto.CompleteClientCommandRequest;
import com.aiapi.client.dto.CreateClientAgentSessionRequest;
import com.aiapi.client.dto.DispatchClientCommandRequest;
import com.aiapi.client.dto.RegisterClientNodeRequest;
import com.aiapi.client.service.ClientExecutionService;
import com.aiapi.client.service.ClientNodeService;
import com.aiapi.common.api.ApiResponse;
import com.aiapi.common.enums.ClientNodeStatus;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientNodeController {

    private final ClientNodeService clientNodeService;
    private final ClientExecutionService clientExecutionService;

    @GetMapping
    public ApiResponse<List<ClientNodeResponse>> list() {
        return ApiResponse.success(clientNodeService.list());
    }

    @GetMapping("/{clientCode}")
    public ApiResponse<ClientNodeResponse> get(@PathVariable String clientCode) {
        return ApiResponse.success(clientNodeService.get(clientCode));
    }

    @PostMapping("/register")
    public ApiResponse<ClientNodeResponse> register(@Valid @RequestBody RegisterClientNodeRequest request) {
        ClientNodeStatus previousStatus = clientNodeService.statusOf(request.getClientCode());
        if (previousStatus == null || previousStatus == ClientNodeStatus.OFFLINE) {
            clientExecutionService.clearClientRuntime(request.getClientCode());
        }
        ClientNodeResponse response = clientNodeService.register(request);
        clientExecutionService.prepareClientWorkflowSessions(response.getClientCode());
        return ApiResponse.success(response);
    }

    @PostMapping("/{clientCode}/heartbeat")
    public ApiResponse<ClientNodeResponse> heartbeat(@PathVariable String clientCode) {
        ClientNodeResponse response = clientNodeService.heartbeat(clientCode);
        clientExecutionService.prepareClientWorkflowSessions(response.getClientCode());
        return ApiResponse.success(response);
    }

    @PostMapping("/{clientCode}/offline")
    public ApiResponse<Void> offline(@PathVariable String clientCode) {
        clientExecutionService.releaseClientRunningTasks(clientCode);
        clientNodeService.offline(clientCode);
        return ApiResponse.success();
    }

    @GetMapping("/{clientCode}/sessions")
    public ApiResponse<List<ClientAgentSessionResponse>> listSessions(@PathVariable String clientCode,
                                                                      @RequestParam(required = false) String agentCode) {
        return ApiResponse.success(clientExecutionService.listSessions(clientCode, agentCode));
    }

    @PostMapping("/{clientCode}/sessions")
    public ApiResponse<ClientAgentSessionResponse> createSession(@PathVariable String clientCode,
                                                                @Valid @RequestBody CreateClientAgentSessionRequest request) {
        return ApiResponse.success(clientExecutionService.createSession(clientCode, request));
    }

    @GetMapping("/{clientCode}/sessions/pending-create")
    public ApiResponse<List<ClientAgentSessionResponse>> listPendingSessionCreations(@PathVariable String clientCode) {
        return ApiResponse.success(clientExecutionService.listPendingSessionCreations(clientCode));
    }

    @PostMapping("/{clientCode}/sessions/{requestId}/complete")
    public ApiResponse<ClientAgentSessionResponse> completeSessionCreation(@PathVariable String clientCode,
                                                                          @PathVariable String requestId,
                                                                          @RequestBody CompleteClientAgentSessionRequest request) {
        return ApiResponse.success(clientExecutionService.completeSessionCreation(clientCode, requestId, request));
    }

    @PostMapping("/{clientCode}/sessions/{sessionId}/events")
    public ApiResponse<ClientAgentSessionResponse> appendSessionEvent(@PathVariable String clientCode,
                                                                      @PathVariable String sessionId,
                                                                      @Valid @RequestBody AppendClientSessionEventRequest request) {
        return ApiResponse.success(clientExecutionService.appendClientSessionEvent(clientCode, sessionId, request));
    }

    @PutMapping("/{clientCode}/sessions/{sessionId}/default")
    public ApiResponse<ClientAgentSessionResponse> setDefaultSession(@PathVariable String clientCode,
                                                                    @PathVariable String sessionId) {
        return ApiResponse.success(clientExecutionService.setDefaultSession(clientCode, sessionId));
    }

    @PostMapping("/dispatch")
    public ApiResponse<ClientCommandResponse> dispatch(@Valid @RequestBody DispatchClientCommandRequest request) {
        return ApiResponse.success(clientExecutionService.dispatch(request));
    }

    @GetMapping("/{clientCode}/commands")
    public ApiResponse<List<ClientCommandResponse>> listCommands(@PathVariable String clientCode) {
        return ApiResponse.success(clientExecutionService.listCommands(clientCode));
    }

    @GetMapping("/{clientCode}/commands/pending")
    public ApiResponse<List<ClientCommandResponse>> listPendingCommands(@PathVariable String clientCode) {
        return ApiResponse.success(clientExecutionService.listPendingCommands(clientCode));
    }

    @PostMapping("/{clientCode}/commands/{commandId}/start")
    public ApiResponse<ClientCommandResponse> startCommand(@PathVariable String clientCode,
                                                          @PathVariable String commandId) {
        return ApiResponse.success(clientExecutionService.startCommand(clientCode, commandId));
    }

    @PostMapping("/{clientCode}/commands/{commandId}/complete")
    public ApiResponse<ClientCommandResponse> completeCommand(@PathVariable String clientCode,
                                                             @PathVariable String commandId,
                                                             @RequestBody CompleteClientCommandRequest request) {
        return ApiResponse.success(clientExecutionService.completeCommand(clientCode, commandId, request));
    }
}
