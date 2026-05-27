package com.aiapi.project.repository;

import com.aiapi.common.enums.AgentRole;
import com.aiapi.common.enums.ProjectMarkdownBaseKey;
import com.aiapi.common.enums.ProjectMarkdownFileType;
import com.aiapi.project.entity.ProjectMarkdownFile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMarkdownFileRepository extends JpaRepository<ProjectMarkdownFile, Long> {

    List<ProjectMarkdownFile> findByProjectCode(String projectCode);

    Optional<ProjectMarkdownFile> findByProjectCodeAndId(String projectCode, Long id);

    @Query("""
            select f from ProjectMarkdownFile f
            where f.projectCode = :projectCode
              and ((:agentRole is null and f.agentRole is null) or f.agentRole = :agentRole)
              and f.fileType = :fileType
              and f.baseKey = :baseKey
            """)
    Optional<ProjectMarkdownFile> findBaseFile(@Param("projectCode") String projectCode,
                                               @Param("agentRole") AgentRole agentRole,
                                               @Param("fileType") ProjectMarkdownFileType fileType,
                                               @Param("baseKey") ProjectMarkdownBaseKey baseKey);

    void deleteByProjectCode(String projectCode);
}
