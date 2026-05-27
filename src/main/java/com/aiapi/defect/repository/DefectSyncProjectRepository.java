package com.aiapi.defect.repository;

import com.aiapi.defect.entity.DefectSyncProject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefectSyncProjectRepository extends JpaRepository<DefectSyncProject, Long> {

    Optional<DefectSyncProject> findBySyncCode(String syncCode);

    List<DefectSyncProject> findByProjectCode(String projectCode);
}
