package com.aiapi.requirement.repository;

import com.aiapi.common.enums.RequirementStatus;
import com.aiapi.requirement.entity.RequirementModuleInfo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementModuleInfoRepository extends JpaRepository<RequirementModuleInfo, Long> {

    Optional<RequirementModuleInfo> findByRequirementNo(String requirementNo);

    long countByProjectCode(String projectCode);

    List<RequirementModuleInfo> findByProjectCode(String projectCode);

    long countByMainAgentCode(String mainAgentCode);

    List<RequirementModuleInfo> findByRootRequirementNo(String rootRequirementNo);

    List<RequirementModuleInfo> findByParentRequirementNoOrderBySortNoAscCreatedAtAsc(String parentRequirementNo);

    long countByParentRequirementNo(String parentRequirementNo);

    List<RequirementModuleInfo> findByMainAgentCodeAndStatusIn(String mainAgentCode, Collection<RequirementStatus> statuses);
}
