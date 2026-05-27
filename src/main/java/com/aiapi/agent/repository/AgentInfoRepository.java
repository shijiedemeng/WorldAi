package com.aiapi.agent.repository;

import com.aiapi.agent.entity.AgentInfo;
import com.aiapi.common.enums.AgentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentInfoRepository extends JpaRepository<AgentInfo, Long> {

    Optional<AgentInfo> findByAgentCode(String agentCode);

    List<AgentInfo> findByAgentCodeIn(List<String> agentCodes);

    long countByProjectCode(String projectCode);

    List<AgentInfo> findByProjectCode(String projectCode);

    List<AgentInfo> findByStatus(AgentStatus status);
}
