package com.aiapi.defect.service;

import com.aiapi.common.enums.DefectPlatformType;
import com.aiapi.common.exception.BizException;
import com.aiapi.defect.dto.FetchYunxiaoOrganizationsRequest;
import com.aiapi.defect.dto.FetchYunxiaoProjectMembersRequest;
import com.aiapi.defect.dto.FetchYunxiaoProjectsRequest;
import com.aiapi.defect.dto.YunxiaoProjectResponse;
import com.aiapi.defect.dto.YunxiaoOrganizationResponse;
import com.aiapi.defect.dto.YunxiaoProjectMemberResponse;
import com.aiapi.defect.entity.DefectSyncAccount;
import com.aiapi.defect.entity.DefectSyncProject;
import com.aiapi.defect.integration.YunxiaoDefectPlatformClient;
import com.aiapi.defect.integration.YunxiaoOrganization;
import com.aiapi.defect.integration.YunxiaoProjectMember;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class YunxiaoMetadataService {

    private final YunxiaoDefectPlatformClient yunxiaoClient;
    private final DefectSourceConfigService defectSourceConfigService;
    private final ObjectMapper objectMapper;

    public List<YunxiaoOrganizationResponse> listOrganizations(FetchYunxiaoOrganizationsRequest request) {
        return yunxiaoClient.fetchOrganizations(request.getBaseUrl(), request.getAccessToken(), request.getUserId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<YunxiaoProjectResponse> listProjects(FetchYunxiaoProjectsRequest request) {
        return yunxiaoClient.fetchProjects(request.getBaseUrl(), request.getAccessToken(), request.getOrganizationId());
    }

    public List<YunxiaoProjectMemberResponse> listProjectMembers(FetchYunxiaoProjectMembersRequest request) {
        return yunxiaoClient.fetchProjectMembers(
                        request.getBaseUrl(),
                        request.getAccessToken(),
                        request.getOrganizationId(),
                        request.getProjectId(),
                        request.getName(),
                        request.getRoleId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<YunxiaoProjectMemberResponse> listSourceProjectMembers(String sourceCode, String name, String roleId) {
        DefectSyncAccount account = defectSourceConfigService.findSourceAccount(sourceCode);
        if (account.getPlatformType() != DefectPlatformType.YUNXIAO) {
            throw new BizException(400, "only yunxiao source supports member fetch");
        }
        DefectSyncProject syncProject = defectSourceConfigService.findSourceProject(sourceCode);
        String organizationId = readOrganizationId(account.getExtraConfig());
        String projectId = trimToNull(syncProject.getExternalProjectKey());
        if (projectId == null) {
            throw new BizException(400, "yunxiao project is required");
        }
        return yunxiaoClient.fetchProjectMembers(
                        account.getBaseUrl(),
                        account.getAccessToken(),
                        organizationId,
                        projectId,
                        name,
                        roleId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public YunxiaoProjectResponse fetchProject(String sourceCode, String projectId) {
        DefectSyncAccount account = defectSourceConfigService.findSourceAccount(sourceCode);
        if (account.getPlatformType() != DefectPlatformType.YUNXIAO) {
            throw new BizException(400, "only yunxiao source supports project fetch");
        }
        String organizationId = readOrganizationId(account.getExtraConfig());
        String normalizedProjectId = trimToNull(projectId);
        if (normalizedProjectId == null) {
            DefectSyncProject syncProject = defectSourceConfigService.findSourceProject(sourceCode);
            normalizedProjectId = trimToNull(syncProject.getExternalProjectKey());
        }
        if (normalizedProjectId == null) {
            throw new BizException(400, "projectId is required");
        }
        return yunxiaoClient.fetchProject(
                account.getBaseUrl(),
                account.getAccessToken(),
                organizationId,
                normalizedProjectId);
    }

    private YunxiaoOrganizationResponse toResponse(YunxiaoOrganization organization) {
        return YunxiaoOrganizationResponse.builder()
                .id(organization.getId())
                .name(organization.getName())
                .description(organization.getDescription())
                .creatorId(organization.getCreatorId())
                .defaultRole(organization.getDefaultRole())
                .createdAt(organization.getCreatedAt())
                .updatedAt(organization.getUpdatedAt())
                .build();
    }

    private YunxiaoProjectMemberResponse toResponse(YunxiaoProjectMember member) {
        return YunxiaoProjectMemberResponse.builder()
                .roleId(member.getRoleId())
                .roleName(member.getRoleName())
                .userAvatar(member.getUserAvatar())
                .userId(member.getUserId())
                .userName(member.getUserName())
                .build();
    }

    private String readOrganizationId(String extraConfig) {
        String configText = trimToNull(extraConfig);
        if (configText == null) {
            throw new BizException(400, "yunxiao extraConfig.organizationId is required");
        }
        try {
            JsonNode root = objectMapper.readTree(configText);
            String organizationId = trimToNull(firstNonBlank(readText(root, "organizationId"), readText(root, "organization_id")));
            if (organizationId == null) {
                throw new BizException(400, "yunxiao extraConfig.organizationId is required");
            }
            return organizationId;
        } catch (JsonProcessingException ex) {
            throw new BizException(400, "yunxiao extraConfig must be valid JSON: " + ex.getMessage());
        }
    }

    private String readText(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
