package com.aiapi.session.repository;

import com.aiapi.session.entity.AgentSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSessionRepository extends JpaRepository<AgentSession, Long> {

    Optional<AgentSession> findBySessionCode(String sessionCode);

    Optional<AgentSession> findByClientCodeAndAgentCodeAndExternalSessionId(String clientCode, String agentCode, String externalSessionId);

    long countByProjectCode(String projectCode);

    long countByAgentCode(String agentCode);

    List<AgentSession> findByAgentCodeAndReusableFlagTrue(String agentCode);
}
