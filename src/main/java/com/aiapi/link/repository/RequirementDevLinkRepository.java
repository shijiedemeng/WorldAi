package com.aiapi.link.repository;

import com.aiapi.common.enums.LinkStatus;
import com.aiapi.link.entity.RequirementDevLink;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementDevLinkRepository extends JpaRepository<RequirementDevLink, Long> {

    List<RequirementDevLink> findByRequirementNo(String requirementNo);

    List<RequirementDevLink> findByRequirementNoIn(Set<String> requirementNos);

    long countByRequirementNo(String requirementNo);

    void deleteByRequirementNo(String requirementNo);

    long countByAgentCode(String agentCode);

    List<RequirementDevLink> findByAgentCodeAndStatusIn(String agentCode, Collection<LinkStatus> statuses);
}
