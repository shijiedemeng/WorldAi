package com.aiapi.project.repository;

import com.aiapi.common.enums.ProjectDocumentUsage;
import com.aiapi.project.entity.ProjectMarkdownDocumentLink;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMarkdownDocumentLinkRepository extends JpaRepository<ProjectMarkdownDocumentLink, Long> {

    List<ProjectMarkdownDocumentLink> findByProjectCode(String projectCode);

    List<ProjectMarkdownDocumentLink> findByProjectCodeAndUsageTypeOrderBySortNoAsc(String projectCode, ProjectDocumentUsage usageType);

    void deleteByProjectCode(String projectCode);
}
