package com.aiapi.session.repository;

import com.aiapi.session.entity.RequirementSessionBinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementSessionBindingRepository extends JpaRepository<RequirementSessionBinding, Long> {

    long countByRequirementNo(String requirementNo);

    void deleteByRequirementNo(String requirementNo);

    List<RequirementSessionBinding> findByRequirementNoOrderByPrimaryFlagDescCreatedAtAsc(String requirementNo);

    List<RequirementSessionBinding> findBySessionCode(String sessionCode);
}
