package com.aiapi.agent.controller;

import com.aiapi.agent.dto.AgentResponse;
import com.aiapi.agent.dto.CreateAgentRequest;
import com.aiapi.agent.dto.UpdateAgentRequest;
import com.aiapi.agent.service.AgentService;
import com.aiapi.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping
    public ApiResponse<AgentResponse> create(@Valid @RequestBody CreateAgentRequest request) {
        return ApiResponse.success(agentService.create(request));
    }

    @GetMapping
    public ApiResponse<List<AgentResponse>> list(@RequestParam(required = false) String projectCode) {
        return ApiResponse.success(agentService.list(projectCode));
    }

    @GetMapping("/{agentCode}")
    public ApiResponse<AgentResponse> get(@PathVariable String agentCode) {
        return ApiResponse.success(agentService.get(agentCode));
    }

    @PutMapping("/{agentCode}")
    public ApiResponse<AgentResponse> update(@PathVariable String agentCode,
                                             @Valid @RequestBody UpdateAgentRequest request) {
        return ApiResponse.success(agentService.update(agentCode, request));
    }

    @DeleteMapping("/{agentCode}")
    public ApiResponse<Void> delete(@PathVariable String agentCode) {
        agentService.delete(agentCode);
        return ApiResponse.success();
    }

    @PostMapping("/{agentCode}/heartbeat")
    public ApiResponse<AgentResponse> heartbeat(@PathVariable String agentCode) {
        return ApiResponse.success(agentService.heartbeat(agentCode));
    }
}
