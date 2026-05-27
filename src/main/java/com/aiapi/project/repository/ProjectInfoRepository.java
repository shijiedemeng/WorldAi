package com.aiapi.project.repository;

import com.aiapi.project.entity.ProjectInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectInfoRepository extends JpaRepository<ProjectInfo, Long> {

    Optional<ProjectInfo> findByProjectCode(String projectCode);

    long countByOwnerAgentCode(String ownerAgentCode);
}
