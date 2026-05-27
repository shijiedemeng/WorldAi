package com.aiapi.skill.repository;

import com.aiapi.skill.entity.SkillInfo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SkillInfoRepository extends JpaRepository<SkillInfo, Long>, JpaSpecificationExecutor<SkillInfo> {

    Optional<SkillInfo> findBySkillCode(String skillCode);

    List<SkillInfo> findBySkillCodeIn(Collection<String> skillCodes);

    long countBySkillCodeIn(Collection<String> skillCodes);
}
