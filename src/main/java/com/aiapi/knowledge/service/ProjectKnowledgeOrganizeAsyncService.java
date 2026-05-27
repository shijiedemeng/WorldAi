package com.aiapi.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ProjectKnowledgeOrganizeAsyncService {

    private final ProjectKnowledgeReserveService projectKnowledgeReserveService;

    public ProjectKnowledgeOrganizeAsyncService(@Lazy ProjectKnowledgeReserveService projectKnowledgeReserveService) {
        this.projectKnowledgeReserveService = projectKnowledgeReserveService;
    }

    @Async("aiApiTaskExecutor")
    public void submit(Long id) {
        try {
            projectKnowledgeReserveService.executeOrganize(id);
        } catch (Exception ex) {
            log.error("project knowledge organize failed for {}: {}", id, ex.getMessage(), ex);
            projectKnowledgeReserveService.markOrganizeFailed(id, ex.getMessage());
        }
    }
}
