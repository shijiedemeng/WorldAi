package com.aiapi.project.repository;

import com.aiapi.common.enums.AgentRole;
import com.aiapi.common.enums.ProjectMarkdownBaseKey;
import com.aiapi.project.entity.ProjectMarkdownFileHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMarkdownFileHistoryRepository extends JpaRepository<ProjectMarkdownFileHistory, Long> {

    @Query("""
            select h from ProjectMarkdownFileHistory h
            where h.projectCode = :projectCode
              and ((:agentRole is null and h.agentRole is null) or h.agentRole = :agentRole)
              and h.baseKey = :baseKey
            order by h.versionNo desc, h.createdAt desc
            """)
    List<ProjectMarkdownFileHistory> findBaseHistory(@Param("projectCode") String projectCode,
                                                     @Param("agentRole") AgentRole agentRole,
                                                     @Param("baseKey") ProjectMarkdownBaseKey baseKey);

    Optional<ProjectMarkdownFileHistory> findByProjectCodeAndId(String projectCode, Long id);

    void deleteByProjectCode(String projectCode);
}
