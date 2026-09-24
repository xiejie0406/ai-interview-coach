package com.ruoyi.fashion.infrastructure.persistence.agent;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ruoyi.fashion.application.agent.AgentVersionDraft;
import com.ruoyi.fashion.application.agent.AgentVersionView;
import com.ruoyi.fashion.application.agent.AgentView;
import com.ruoyi.fashion.application.agent.port.FashionAgentRepository;
import com.ruoyi.fashion.infrastructure.persistence.foundation.FashionJsonCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFashionAgentRepository implements FashionAgentRepository {
    private static final String VERSION_COLUMNS = "id,agent_id,version_no,provider_code,model_name,system_instruction,"
            + "model_config_json,tools_json,handoffs_json,input_schema_json,output_schema_json,guardrails_json,"
            + "max_steps,timeout_seconds,config_hash,status,published_by,published_at,row_version";

    private final NamedParameterJdbcTemplate jdbc;
    private final FashionJsonCodec json;

    public JdbcFashionAgentRepository(
            @Qualifier("fashionJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            FashionJsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<AgentView> findAll() {
        return jdbc.query("select id,agent_code,name,agent_type,description,current_version_id,status,row_version "
                + "from fq_ai_agent order by agent_type,agent_code", this::mapAgent);
    }

    @Override
    public Optional<AgentView> findAgent(long id) {
        return jdbc.query("select id,agent_code,name,agent_type,description,current_version_id,status,row_version "
                + "from fq_ai_agent where id=:id", Map.of("id", id), this::mapAgent).stream().findFirst();
    }

    @Override
    public Optional<AgentView> findByCode(String agentCode) {
        return jdbc.query("select id,agent_code,name,agent_type,description,current_version_id,status,row_version "
                + "from fq_ai_agent where agent_code=:code", Map.of("code", agentCode), this::mapAgent)
                .stream().findFirst();
    }

    @Override
    public List<AgentVersionView> findVersions(long agentId) {
        return jdbc.query("select " + VERSION_COLUMNS + " from fq_ai_agent_version where agent_id=:agentId "
                + "order by version_no desc", Map.of("agentId", agentId), this::mapVersion);
    }

    @Override
    public Optional<AgentVersionView> findVersion(long versionId) {
        return jdbc.query("select " + VERSION_COLUMNS + " from fq_ai_agent_version where id=:id",
                Map.of("id", versionId), this::mapVersion).stream().findFirst();
    }

    @Override
    public Optional<AgentVersionView> findCurrentVersionByType(String agentType) {
        return jdbc.query("select v." + VERSION_COLUMNS.replace(",", ",v.")
                        + " from fq_ai_agent a join fq_ai_agent_version v on v.id=a.current_version_id"
                        + " where a.agent_type=:type and a.status='active' and v.status='published'"
                        + " order by a.id limit 1",
                Map.of("type", agentType), this::mapVersion).stream().findFirst();
    }

    @Override
    public void insertAgent(
            long id, String code, String name, String type, String description, long operatorId, Instant now) {
        jdbc.update("""
                insert into fq_ai_agent (id,agent_code,name,agent_type,description,current_version_id,status,
                    create_by,create_time,update_by,update_time,row_version)
                values (:id,:code,:name,:type,:description,null,'active',:operator,:now,:operator,:now,1)
                """, new MapSqlParameterSource().addValue("id", id).addValue("code", code)
                .addValue("name", name).addValue("type", type).addValue("description", description)
                .addValue("operator", operatorId).addValue("now", Timestamp.from(now)));
    }

    @Override
    public AgentVersionView insertVersion(
            long id, long agentId, int versionNo, AgentVersionDraft draft, String configHash,
            long operatorId, Instant now) {
        jdbc.update("""
                insert into fq_ai_agent_version (
                    id,agent_id,version_no,provider_code,model_name,system_instruction,model_config_json,tools_json,
                    handoffs_json,input_schema_json,output_schema_json,guardrails_json,max_steps,timeout_seconds,
                    config_hash,status,published_by,published_at,create_by,create_time,update_by,update_time,row_version)
                values (:id,:agentId,:versionNo,:provider,:model,:instruction,:modelConfig,:tools,:handoffs,
                    :inputSchema,:outputSchema,:guardrails,:maxSteps,:timeout,:hash,'draft',null,null,
                    :operator,:now,:operator,:now,1)
                """, versionParameters(id, agentId, versionNo, draft, configHash, operatorId, now));
        return findVersion(id).orElseThrow();
    }

    @Override
    public int nextVersion(long agentId) {
        Integer value = jdbc.queryForObject(
                "select coalesce(max(version_no),0)+1 from fq_ai_agent_version where agent_id=:agentId for update",
                Map.of("agentId", agentId), Integer.class);
        return value == null ? 1 : value;
    }

    @Override
    public boolean publishVersion(
            long agentId, long versionId, long expectedVersion, long operatorId, Instant now) {
        int target = jdbc.update("""
                update fq_ai_agent_version set status='published',published_by=:operator,published_at=:now,
                    update_by=:operator,update_time=:now,row_version=row_version+1
                 where id=:versionId and agent_id=:agentId and status='draft' and row_version=:version
                """, new MapSqlParameterSource().addValue("versionId", versionId).addValue("agentId", agentId)
                .addValue("version", expectedVersion).addValue("operator", operatorId)
                .addValue("now", Timestamp.from(now)));
        if (target != 1) return false;
        jdbc.update("""
                update fq_ai_agent_version set status='retired',update_by=:operator,update_time=:now,
                    row_version=row_version+1
                 where agent_id=:agentId and id<>:versionId and status='published'
                """, new MapSqlParameterSource().addValue("versionId", versionId).addValue("agentId", agentId)
                .addValue("operator", operatorId).addValue("now", Timestamp.from(now)));
        return jdbc.update("""
                update fq_ai_agent set current_version_id=:versionId,update_by=:operator,update_time=:now,
                    row_version=row_version+1 where id=:agentId and status='active'
                """, new MapSqlParameterSource().addValue("versionId", versionId).addValue("agentId", agentId)
                .addValue("operator", operatorId).addValue("now", Timestamp.from(now))) == 1;
    }

    private MapSqlParameterSource versionParameters(
            long id, long agentId, int versionNo, AgentVersionDraft d, String hash, long operator, Instant now) {
        return new MapSqlParameterSource().addValue("id", id).addValue("agentId", agentId)
                .addValue("versionNo", versionNo).addValue("provider", d.providerCode())
                .addValue("model", d.modelName()).addValue("instruction", d.systemInstruction())
                .addValue("modelConfig", json.write(d.modelConfig())).addValue("tools", json.write(d.tools()))
                .addValue("handoffs", json.write(d.handoffs())).addValue("inputSchema", json.write(d.inputSchema()))
                .addValue("outputSchema", json.write(d.outputSchema())).addValue("guardrails", json.write(d.guardrails()))
                .addValue("maxSteps", d.maxSteps()).addValue("timeout", d.timeoutSeconds()).addValue("hash", hash)
                .addValue("operator", operator).addValue("now", Timestamp.from(now));
    }

    private AgentView mapAgent(ResultSet rs, int rowNum) throws SQLException {
        Long current = nullableLong(rs, "current_version_id");
        return new AgentView(Long.toString(rs.getLong("id")), rs.getString("agent_code"), rs.getString("name"),
                rs.getString("agent_type"), rs.getString("description"),
                current == null ? null : Long.toString(current), rs.getString("status"), rs.getLong("row_version"));
    }

    private AgentVersionView mapVersion(ResultSet rs, int rowNum) throws SQLException {
        Long publishedBy = nullableLong(rs, "published_by");
        Timestamp publishedAt = rs.getTimestamp("published_at");
        return new AgentVersionView(Long.toString(rs.getLong("id")), Long.toString(rs.getLong("agent_id")),
                rs.getInt("version_no"), rs.getString("provider_code"), rs.getString("model_name"),
                rs.getString("system_instruction"), json.readTree(rs.getString("model_config_json")),
                json.readTree(rs.getString("tools_json")), json.readTree(rs.getString("handoffs_json")),
                json.readTree(rs.getString("input_schema_json")), json.readTree(rs.getString("output_schema_json")),
                json.readTree(rs.getString("guardrails_json")), rs.getInt("max_steps"),
                rs.getInt("timeout_seconds"), rs.getString("config_hash"), rs.getString("status"),
                publishedBy == null ? null : Long.toString(publishedBy),
                publishedAt == null ? null : publishedAt.toInstant(), rs.getLong("row_version"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
