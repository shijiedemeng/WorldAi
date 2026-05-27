package com.aiapi.requirement.repository;

import com.aiapi.common.enums.RequirementType;
import com.aiapi.requirement.entity.RequirementInfo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementInfoRepository extends JpaRepository<RequirementInfo, Long> {

    Optional<RequirementInfo> findByRequirementNo(String requirementNo);

    long countByProjectCode(String projectCode);

    List<RequirementInfo> findByProjectCode(String projectCode);

    List<RequirementInfo> findByProjectCodeAndRequirementType(String projectCode, RequirementType requirementType);

    long countByMainAgentCode(String mainAgentCode);

    List<RequirementInfo> findByRootRequirementNo(String rootRequirementNo);

    List<RequirementInfo> findByRootRequirementNoAndRequirementType(String rootRequirementNo, RequirementType requirementType);

    List<RequirementInfo> findByParentRequirementNoOrderBySortNoAscCreatedAtAsc(String parentRequirementNo);

    long countByParentRequirementNo(String parentRequirementNo);
}
