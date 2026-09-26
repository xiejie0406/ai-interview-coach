package com.ruoyi.fashion.application.agent;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.agent.port.FashionAgentRepository;
import com.ruoyi.fashion.domain.shared.FashionId;
import com.ruoyi.fashion.domain.shared.FashionIdGenerator;
import com.ruoyi.fashion.domain.shared.FashionTimeSource;
import com.ruoyi.fashion.infrastructure.storage.FashionHashing;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@FashionModuleEnabled
@Service
public class FashionAgentService {
    private static final Set<String> TYPES = Set.of(
            "orchestrator", "requirement", "selection", "styling", "quotation", "image");

    private final FashionAgentRepository repository;
    private final FashionIdGenerator ids;
    private final FashionTimeSource time;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    public FashionAgentService(
            FashionAgentRepository repository,
            FashionIdGenerator ids,
            FashionTimeSource time,
            ObjectMapper objectMapper,
            @Qualifier("fashionTransactionTemplate") TransactionTemplate transaction) {
        this.repository = repository;
        this.ids = ids;
        this.time = time;
        this.objectMapper = objectMapper;
        this.transaction = transaction;
    }

    public List<AgentView> list() {
        return repository.findAll();
    }

    public List<AgentVersionView> versions(String agentId) {
        long id = FashionId.parse(agentId).value();
        repository.findAgent(id).orElseThrow(() -> new ServiceException("Agent 不存在"));
        return repository.findVersions(id);
    }

    public AgentView create(
            String code, String name, String type, String description, long operatorId) {
        requireCode("agentCode", code, 64);
        requireText("Agent 名称", name, 100);
        if (!TYPES.contains(type)) {
            throw new ServiceException("Agent 类型无效");
        }
        if (description != null && description.length() > 500) {
            throw new ServiceException("Agent 说明长度不能超过 500");
        }
        return transaction.execute(status -> {
            if (repository.findByCode(code.trim()).isPresent()) {
                throw new ServiceException("Agent 编码已存在：" + code.trim());
            }
            long id = ids.nextId();
            repository.insertAgent(id, code.trim(), name.trim(), type, trim(description), operatorId, time.now());
            return repository.findAgent(id).orElseThrow();
        });
    }

    public AgentVersionView createVersion(String agentId, AgentVersionDraft draft, long operatorId) {
        long id = FashionId.parse(agentId).value();
        repository.findAgent(id).orElseThrow(() -> new ServiceException("Agent 不存在"));
        validateDraft(draft);
        return transaction.execute(status -> repository.insertVersion(
                ids.nextId(), id, repository.nextVersion(id), draft, hash(draft), operatorId, time.now()));
    }

    public AgentVersionView publish(
            String agentId, String versionId, long expectedRowVersion, long operatorId) {
        long agent = FashionId.parse(agentId).value();
        long version = FashionId.parse(versionId).value();
        return transaction.execute(status -> {
            AgentVersionView target = repository.findVersion(version)
                    .filter(value -> value.agentId().equals(Long.toString(agent)))
                    .orElseThrow(() -> new ServiceException("Agent 版本不存在"));
            if (!"draft".equals(target.status())) {
                throw new ServiceException("只有草稿 Agent 版本可发布");
            }
            if (!repository.publishVersion(agent, version, expectedRowVersion, operatorId, time.now())) {
                throw new ServiceException("Agent 版本已被修改，请刷新后重试", 409);
            }
            return repository.findVersion(version).orElseThrow();
        });
    }

    private void validateDraft(AgentVersionDraft draft) {
        if (draft == null) throw new ServiceException("Agent 版本不能为空");
        requireCode("providerCode", draft.providerCode(), 64);
        requireText("模型名称", draft.modelName(), 128);
        requireText("系统指令", draft.systemInstruction(), 20000);
        requireObject("modelConfig", draft.modelConfig());
        requireArray("tools", draft.tools());
        requireArray("handoffs", draft.handoffs());
        requireObject("inputSchema", draft.inputSchema());
        requireObject("outputSchema", draft.outputSchema());
        requireObject("guardrails", draft.guardrails());
        if (draft.maxSteps() < 1 || draft.maxSteps() > 50) {
            throw new ServiceException("maxSteps 必须为 1～50");
        }
        if (draft.timeoutSeconds() < 1 || draft.timeoutSeconds() > 600) {
            throw new ServiceException("timeoutSeconds 必须为 1～600");
        }
        String encoded = new String(write(draft), StandardCharsets.UTF_8);
        if (encoded.length() > 200_000) {
            throw new ServiceException("Agent 版本配置超过 200000 字符");
        }
    }

    private String hash(AgentVersionDraft draft) {
        return FashionHashing.sha256(write(draft));
    }

    private byte[] write(AgentVersionDraft draft) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("providerCode", draft.providerCode());
        value.put("modelName", draft.modelName());
        value.put("systemInstruction", draft.systemInstruction());
        value.put("modelConfig", draft.modelConfig());
        value.put("tools", draft.tools());
        value.put("handoffs", draft.handoffs());
        value.put("inputSchema", draft.inputSchema());
        value.put("outputSchema", draft.outputSchema());
        value.put("guardrails", draft.guardrails());
        value.put("maxSteps", draft.maxSteps());
        value.put("timeoutSeconds", draft.timeoutSeconds());
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception exception) {
            throw new ServiceException("Agent 版本配置无法编码");
        }
    }

    private static void requireObject(String name, JsonNode value) {
        if (value == null || !value.isObject()) throw new ServiceException(name + " 必须是 JSON 对象");
    }

    private static void requireArray(String name, JsonNode value) {
        if (value == null || !value.isArray()) throw new ServiceException(name + " 必须是 JSON 数组");
    }

    private static void requireCode(String name, String value, int max) {
        requireText(name, value, max);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*")) {
            throw new ServiceException(name + " 格式无效");
        }
    }

    private static void requireText(String name, String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new ServiceException(name + "必须为 1～" + max + " 个字符");
        }
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
