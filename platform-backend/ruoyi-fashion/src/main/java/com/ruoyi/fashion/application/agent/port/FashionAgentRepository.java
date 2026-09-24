package com.ruoyi.fashion.application.agent.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ruoyi.fashion.application.agent.AgentVersionDraft;
import com.ruoyi.fashion.application.agent.AgentVersionView;
import com.ruoyi.fashion.application.agent.AgentView;

public interface FashionAgentRepository {
    List<AgentView> findAll();

    Optional<AgentView> findAgent(long id);

    Optional<AgentView> findByCode(String agentCode);

    List<AgentVersionView> findVersions(long agentId);

    Optional<AgentVersionView> findVersion(long versionId);

    Optional<AgentVersionView> findCurrentVersionByType(String agentType);

    void insertAgent(long id, String code, String name, String type, String description, long operatorId, Instant now);

    AgentVersionView insertVersion(
            long id, long agentId, int versionNo, AgentVersionDraft draft, String configHash,
            long operatorId, Instant now);

    int nextVersion(long agentId);

    boolean publishVersion(long agentId, long versionId, long expectedVersion, long operatorId, Instant now);
}
