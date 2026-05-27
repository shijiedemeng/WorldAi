package com.aiapi.defect.service;

import com.aiapi.common.exception.BizException;
import com.aiapi.common.enums.DefectPlatformType;
import com.aiapi.defect.dto.DefectSourceConfigResponse;
import com.aiapi.defect.dto.SaveDefectSourceConfigRequest;
import com.aiapi.defect.entity.DefectSyncAccount;
import com.aiapi.defect.entity.DefectSyncProject;
import com.aiapi.defect.repository.DefectSyncAccountRepository;
import com.aiapi.defect.repository.DefectSyncProjectRepository;
import com.aiapi.project.service.ProjectService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefectSourceConfigService {

    private final DefectSyncAccountRepository accountRepository;
    private final DefectSyncProjectRepository syncProjectRepository;
    private final ProjectService projectService;

    @Transactional(readOnly = true)
    public List<DefectSourceConfigResponse> list(String projectCode) {
        List<DefectSyncProject> syncProjects = projectCode == null || projectCode.isBlank()
                ? syncProjectRepository.findAll()
                : syncProjectRepository.findByProjectCode(projectCode);
        List<DefectSourceConfigResponse> result = new ArrayList<>();
        for (DefectSyncProject syncProject : syncProjects) {
            DefectSyncAccount account = accountRepository.findByAccountCode(syncProject.getAccountCode()).orElse(null);
            if (account == null) {
                continue;
            }
            result.add(toResponse(syncProject, account));
        }
        result.sort(Comparator.comparing(DefectSourceConfigResponse::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Transactional
    public DefectSourceConfigResponse save(SaveDefectSourceConfigRequest request) {
        projectService.findEntity(request.getProjectCode());

        String sourceCode = request.getSourceCode().trim();
        String projectCode = request.getProjectCode().trim();
        String externalProjectKey = trimToNull(request.getExternalProjectKey());
        String externalProjectName = trimToNull(request.getExternalProjectName());
        if (externalProjectKey == null) {
            throw new BizException(400, "externalProjectKey is required");
        }
        if (request.getPlatformType() != DefectPlatformType.YUNXIAO && externalProjectName == null) {
            throw new BizException(400, "externalProjectName is required");
        }

        DefectSyncAccount account = accountRepository.findByAccountCode(sourceCode).orElseGet(DefectSyncAccount::new);
        account.setAccountCode(sourceCode);
        account.setAccountName(request.getSourceName().trim());
        account.setPlatformType(request.getPlatformType());
        account.setBaseUrl(request.getBaseUrl().trim());
        account.setUsername(trimToNull(request.getUsername()));
        account.setPasswordValue(trimToNull(request.getPasswordValue()));
        account.setAccessToken(trimToNull(request.getAccessToken()));
        account.setEnabledFlag(request.getEnabledFlag());
        account.setExtraConfig(trimToNull(request.getExtraConfig()));
        DefectSyncAccount savedAccount = accountRepository.save(account);

        DefectSyncProject syncProject = syncProjectRepository.findBySyncCode(sourceCode).orElseGet(DefectSyncProject::new);
        syncProject.setSyncCode(sourceCode);
        syncProject.setProjectCode(projectCode);
        syncProject.setAccountCode(savedAccount.getAccountCode());
        syncProject.setPlatformType(savedAccount.getPlatformType());
        syncProject.setExternalProjectKey(externalProjectKey == null ? "" : externalProjectKey);
        String resolvedExternalProjectName = externalProjectName;
        if (savedAccount.getPlatformType() == DefectPlatformType.YUNXIAO && resolvedExternalProjectName == null) {
            resolvedExternalProjectName = trimToNull(externalProjectKey);
        }
        syncProject.setExternalProjectName(resolvedExternalProjectName == null ? "" : resolvedExternalProjectName);
        DefectSyncProject savedProject = syncProjectRepository.save(syncProject);
        return toResponse(savedProject, savedAccount);
    }

    @Transactional
    public DefectSourceConfigResponse updateExternalProject(String sourceCode, String externalProjectKey, String externalProjectName) {
        String normalizedSourceCode = trimToNull(sourceCode);
        String normalizedProjectKey = trimToNull(externalProjectKey);
        if (normalizedSourceCode == null) {
            throw new BizException(400, "sourceCode is required");
        }
        if (normalizedProjectKey == null) {
            throw new BizException(400, "externalProjectKey is required");
        }
        DefectSyncProject syncProject = syncProjectRepository.findBySyncCode(normalizedSourceCode)
                .orElseThrow(() -> new BizException(404, "defect source config not found"));
        DefectSyncAccount account = accountRepository.findByAccountCode(syncProject.getAccountCode())
                .orElseThrow(() -> new BizException(404, "defect source account not found"));
        syncProject.setExternalProjectKey(normalizedProjectKey);
        String resolvedExternalProjectName = trimToNull(externalProjectName);
        if (resolvedExternalProjectName == null) {
            resolvedExternalProjectName = trimToNull(syncProject.getExternalProjectName());
        }
        syncProject.setExternalProjectName(resolvedExternalProjectName == null ? "" : resolvedExternalProjectName);
        DefectSyncProject savedProject = syncProjectRepository.save(syncProject);
        return toResponse(savedProject, account);
    }

    @Transactional(readOnly = true)
    public DefectSyncProject findSourceProject(String sourceCode) {
        return syncProjectRepository.findBySyncCode(sourceCode)
                .orElseThrow(() -> new BizException(404, "defect source config not found"));
    }

    @Transactional(readOnly = true)
    public DefectSourceConfigResponse findBySourceCode(String sourceCode) {
        DefectSyncProject syncProject = findSourceProject(sourceCode);
        DefectSyncAccount account = accountRepository.findByAccountCode(syncProject.getAccountCode())
                .orElseThrow(() -> new BizException(404, "defect source account not found"));
        return toResponse(syncProject, account);
    }

    @Transactional(readOnly = true)
    public DefectSyncAccount findSourceAccount(String sourceCode) {
        return accountRepository.findByAccountCode(sourceCode)
                .or(() -> {
                    Optional<DefectSyncProject> project = syncProjectRepository.findBySyncCode(sourceCode);
                    return project.flatMap(item -> accountRepository.findByAccountCode(item.getAccountCode()));
                })
                .orElseThrow(() -> new BizException(404, "defect source account not found"));
    }

    private DefectSourceConfigResponse toResponse(DefectSyncProject syncProject, DefectSyncAccount account) {
        return DefectSourceConfigResponse.builder()
                .id(syncProject.getId())
                .sourceCode(syncProject.getSyncCode())
                .sourceName(account.getAccountName())
                .projectCode(syncProject.getProjectCode())
                .platformType(account.getPlatformType())
                .baseUrl(account.getBaseUrl())
                .username(account.getUsername())
                .passwordValue(account.getPasswordValue())
                .accessToken(account.getAccessToken())
                .enabledFlag(account.getEnabledFlag())
                .extraConfig(account.getExtraConfig())
                .externalProjectKey(syncProject.getExternalProjectKey())
                .externalProjectName(syncProject.getExternalProjectName())
                .createdAt(syncProject.getCreatedAt())
                .updatedAt(syncProject.getUpdatedAt())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
