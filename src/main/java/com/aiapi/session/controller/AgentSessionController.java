package com.aiapi.session.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.common.enums.AgentSessionStatus;
import com.aiapi.session.dto.AgentSessionResponse;
import com.aiapi.session.dto.CreateAgentSessionRequest;
import com.aiapi.session.dto.UpdateAgentSessionStatusRequest;
import com.aiapi.session.service.AgentSessionService;
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
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class AgentSessionController {

    private final AgentSessionService agentSessionService;

    @PostMapping
    public ApiResponse<AgentSessionResponse> create(@Valid @RequestBody CreateAgentSessionRequest request) {
        return ApiResponse.success(agentSessionService.create(request));
    }

    @GetMapping
    public ApiResponse<List<AgentSessionResponse>> list(@RequestParam(required = false) String projectCode,
                                                        @RequestParam(required = false) String agentCode,
                                                        @RequestParam(required = false) String clientCode,
                                                        @RequestParam(required = false) String rootRequirementNo,
                                                        @RequestParam(required = false) Boolean reusableFlag,
                                                        @RequestParam(required = false) AgentSessionStatus status) {
        return ApiResponse.success(agentSessionService.list(projectCode, agentCode, clientCode, rootRequirementNo, reusableFlag, status));
    }

    @GetMapping("/{sessionCode}")
    public ApiResponse<AgentSessionResponse> get(@PathVariable String sessionCode) {
        return ApiResponse.success(agentSessionService.get(sessionCode));
    }

    @PutMapping("/{sessionCode}/status")
    public ApiResponse<AgentSessionResponse> updateStatus(@PathVariable String sessionCode,
                                                          @Valid @RequestBody UpdateAgentSessionStatusRequest request) {
        return ApiResponse.success(agentSessionService.updateStatus(sessionCode, request));
    }
}
