package com.ruoyi.fashion.infrastructure.persistence.agent;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.ruoyi.fashion.application.agent.AgentVersionView;
import com.ruoyi.fashion.application.agent.run.RunView;
import com.ruoyi.fashion.application.agent.run.RunWorkItem;
import com.ruoyi.fashion.application.agent.run.port.FashionRunRepository;
import com.ruoyi.fashion.application.product.ProductView;
import com.ruoyi.fashion.domain.quote.FashionQuote;
import com.ruoyi.fashion.infrastructure.persistence.foundation.FashionJsonCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@FashionModuleEnabled
@Repository
public class JdbcFashionRunRepository implements FashionRunRepository {
    private static final String RUN_COLUMNS = "id,run_no,conversation_id,agent_version_id,quote_id,quote_row_version,"
            + "request_key,agent_config_hash,deadline_at,context_snapshot_json,output_type,output_json,output_hash,"
            + "validation_json,apply_status,status,current_step_no,run_attempt,execution_lease_id,lease_owner,"
            + "fencing_token,next_retry_at,lease_until,started_at,finished_at,error_code,error_message,row_version";

    private final NamedParameterJdbcTemplate jdbc;
    private final FashionJsonCodec json;

    public JdbcFashionRunRepository(
            @Qualifier("fashionJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            FashionJsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<RunView> findById(long id) {
        return jdbc.query("select " + RUN_COLUMNS + " from fq_ai_run where id=:id", Map.of("id", id), this::mapRun)
                .stream().findFirst();
    }

    @Override
    public Optional<RunView> findByRunNo(String runNo) {
        return jdbc.query("select " + RUN_COLUMNS + " from fq_ai_run where run_no=:runNo",
                Map.of("runNo", runNo), this::mapRun).stream().findFirst();
    }

    @Override
    public Optional<RunView> findByRequestKey(String requestKey) {
        return jdbc.query("select " + RUN_COLUMNS + " from fq_ai_run where request_key=:key",
                Map.of("key", requestKey), this::mapRun).stream().findFirst();
    }

    @Override
    public RunView insertRequirementRun(
            long conversationId,
            long messageId,
            long runId,
            FashionQuote quote,
            AgentVersionView version,
            String sourceText,
            String sourceHash,
            String requestKey,
            Instant deadlineAt,
            long operatorId,
            Instant now) {
        ObjectNode context = json.objectNode();
        context.put("customer_id", Long.toString(quote.customerId()));
        context.put("quote_id", Long.toString(quote.id()));
        context.put("quote_row_version", quote.rowVersion());
        context.putArray("selected_combo_ids");
        context.put("policy", "server_revalidate");
        jdbc.update("""
                insert into fq_ai_conversation (
                    id,conversation_no,owner_user_id,customer_id,quote_id,primary_agent_id,title,channel,
                    context_json,summary_text,summary_up_to_seq,message_count,last_message_at,status,
                    create_by,create_time,update_by,update_time,row_version)
                values (:id,:no,:owner,:customer,:quote,:agent,:title,'web',:context,null,0,1,:now,'active',
                    :owner,:now,:owner,:now,1)
                """, new MapSqlParameterSource().addValue("id", conversationId)
                .addValue("no", "CONV-" + conversationId).addValue("owner", operatorId)
                .addValue("customer", quote.customerId()).addValue("quote", quote.id())
                .addValue("agent", Long.parseLong(version.agentId())).addValue("title", quote.title())
                .addValue("context", json.write(context)).addValue("now", Timestamp.from(now)));

        jdbc.update("""
                insert into fq_ai_message (
                    id,conversation_id,seq_no,parent_message_id,run_id,role,content_text,content_json,
                    attachments_json,provider_item_id,visible_to_user,content_hash,status,error_message,
                    create_by,create_time,update_by,update_time,row_version)
                values (:id,:conversation,1,null,null,'user',:text,null,json_array(),null,1,:hash,'complete',null,
                    :operator,:now,:operator,:now,1)
                """, new MapSqlParameterSource().addValue("id", messageId).addValue("conversation", conversationId)
                .addValue("text", sourceText).addValue("hash", sourceHash).addValue("operator", operatorId)
                .addValue("now", Timestamp.from(now)));

        ObjectNode budget = json.objectNode();
        budget.put("schema_version", "1.0");
        budget.put("max_steps", version.maxSteps());
        budget.put("max_model_calls", 1);
        budget.put("max_tool_calls", 0);
        budget.put("max_handoffs", 0);
        budget.put("max_parallel", 1);
        budget.put("currency", "CNY");
        ObjectNode snapshot = context.deepCopy();
        snapshot.put("source_hash", sourceHash);
        snapshot.put("requirement_confirmed", quote.requirementConfirmed());
        snapshot.set("known_requirements", quote.requirementJson());
        jdbc.update("""
                insert into fq_ai_run (
                    id,run_no,conversation_id,agent_version_id,parent_run_id,input_message_id,trigger_type,quote_id,
                    quote_row_version,combo_id,request_key,agent_config_hash,deadline_at,budget_json,
                    context_snapshot_json,output_type,output_json,output_hash,validation_json,apply_status,
                    apply_request_key,apply_input_hash,applied_by,applied_at,applied_result_json,rejection_reason,
                    trace_id,status,current_step_no,checkpoint_json,input_tokens,cached_tokens,output_tokens,
                    reasoning_tokens,total_cost,cost_currency,run_attempt,execution_lease_id,lease_owner,fencing_token,
                    next_retry_at,lease_until,last_event_seq,started_at,finished_at,error_code,error_message,
                    create_by,create_time,update_by,update_time,row_version)
                values (:id,:no,:conversation,:version,null,:message,'parse_requirement',:quote,:quoteVersion,null,
                    :requestKey,:configHash,:deadline,:budget,:snapshot,null,null,null,null,'not_applied',
                    null,null,null,null,null,null,null,'queued',0,null,null,null,null,null,null,null,0,null,null,0,
                    null,null,0,null,null,null,null,:operator,:now,:operator,:now,1)
                """, new MapSqlParameterSource().addValue("id", runId).addValue("no", "RUN-" + runId)
                .addValue("conversation", conversationId).addValue("version", Long.parseLong(version.id()))
                .addValue("message", messageId).addValue("quote", quote.id()).addValue("quoteVersion", quote.rowVersion())
                .addValue("requestKey", requestKey).addValue("configHash", version.configHash())
                .addValue("deadline", Timestamp.from(deadlineAt)).addValue("budget", json.write(budget))
                .addValue("snapshot", json.write(snapshot)).addValue("operator", operatorId)
                .addValue("now", Timestamp.from(now)));
        return findById(runId).orElseThrow();
    }

    @Override
    public RunView insertProductAttributeRun(
            long conversationId,
            long messageId,
            long runId,
            ProductView product,
            AgentVersionView version,
            String sourceHash,
            String requestKey,
            Instant deadlineAt,
            long operatorId,
            Instant now) {
        ObjectNode productSnapshot = json.objectNode();
        productSnapshot.put("source_ref", product.sourceCode());
        productSnapshot.put("sku_ref", product.skuCode());
        productSnapshot.put("name", product.name());
        productSnapshot.put("category_code", product.categoryCode());
        productSnapshot.put("color_code", product.colorCode());
        productSnapshot.put("color_name", product.colorName());
        putNullable(productSnapshot, "season", product.season());
        productSnapshot.set("tags", json.readTree(json.write(product.tags())));
        ObjectNode context = json.objectNode();
        context.put("product_id", product.id());
        context.put("product_row_version", product.rowVersion());
        context.put("source_hash", sourceHash);
        context.put("policy", "human_confirmation_required");
        context.set("product", productSnapshot);
        jdbc.update("""
                insert into fq_ai_conversation (
                    id,conversation_no,owner_user_id,customer_id,quote_id,primary_agent_id,title,channel,
                    context_json,summary_text,summary_up_to_seq,message_count,last_message_at,status,
                    create_by,create_time,update_by,update_time,row_version)
                values (:id,:no,:owner,null,null,:agent,:title,'web',:context,null,0,1,:now,'active',
                    :owner,:now,:owner,:now,1)
                """, new MapSqlParameterSource().addValue("id", conversationId)
                .addValue("no", "CONV-" + conversationId).addValue("owner", operatorId)
                .addValue("agent", Long.parseLong(version.agentId()))
                .addValue("title", "商品属性建议：" + product.skuCode())
                .addValue("context", json.write(context)).addValue("now", Timestamp.from(now)));
        jdbc.update("""
                insert into fq_ai_message (
                    id,conversation_id,seq_no,parent_message_id,run_id,role,content_text,content_json,
                    attachments_json,provider_item_id,visible_to_user,content_hash,status,error_message,
                    create_by,create_time,update_by,update_time,row_version)
                values (:id,:conversation,1,null,null,'user','请求商品属性建议',:content,json_array(),null,1,
                    :hash,'complete',null,:operator,:now,:operator,:now,1)
                """, new MapSqlParameterSource().addValue("id", messageId)
                .addValue("conversation", conversationId).addValue("content", json.write(productSnapshot))
                .addValue("hash", sourceHash).addValue("operator", operatorId).addValue("now", Timestamp.from(now)));
        ObjectNode budget = json.objectNode();
        budget.put("schema_version", "1.0").put("max_steps", version.maxSteps())
                .put("max_model_calls", 1).put("max_tool_calls", 0).put("max_handoffs", 0)
                .put("max_parallel", 1).put("currency", "CNY");
        jdbc.update("""
                insert into fq_ai_run (
                    id,run_no,conversation_id,agent_version_id,parent_run_id,input_message_id,trigger_type,quote_id,
                    quote_row_version,combo_id,request_key,agent_config_hash,deadline_at,budget_json,
                    context_snapshot_json,output_type,output_json,output_hash,validation_json,apply_status,
                    apply_request_key,apply_input_hash,applied_by,applied_at,applied_result_json,rejection_reason,
                    trace_id,status,current_step_no,checkpoint_json,input_tokens,cached_tokens,output_tokens,
                    reasoning_tokens,total_cost,cost_currency,run_attempt,execution_lease_id,lease_owner,fencing_token,
                    next_retry_at,lease_until,last_event_seq,started_at,finished_at,error_code,error_message,
                    create_by,create_time,update_by,update_time,row_version)
                values (:id,:no,:conversation,:version,null,:message,'select_products',null,null,null,
                    :requestKey,:configHash,:deadline,:budget,:snapshot,null,null,null,null,'not_applied',
                    null,null,null,null,null,null,null,'queued',0,null,null,null,null,null,null,null,0,null,null,0,
                    null,null,0,null,null,null,null,:operator,:now,:operator,:now,1)
                """, new MapSqlParameterSource().addValue("id", runId).addValue("no", "RUN-" + runId)
                .addValue("conversation", conversationId).addValue("version", Long.parseLong(version.id()))
                .addValue("message", messageId).addValue("requestKey", requestKey)
                .addValue("configHash", version.configHash()).addValue("deadline", Timestamp.from(deadlineAt))
                .addValue("budget", json.write(budget)).addValue("snapshot", json.write(context))
                .addValue("operator", operatorId).addValue("now", Timestamp.from(now)));
        return findById(runId).orElseThrow();
    }

    @Override
    public RunView insertSelectionRun(
            long conversationId,
            long messageId,
            long runId,
            FashionQuote quote,
            AgentVersionView version,
            JsonNode selectionSnapshot,
            String sourceHash,
            String requestKey,
            Instant deadlineAt,
            long operatorId,
            Instant now) {
        ObjectNode context = json.objectNode();
        context.put("customer_id", Long.toString(quote.customerId()));
        context.put("quote_id", Long.toString(quote.id()));
        context.put("quote_row_version", quote.rowVersion());
        context.put("source_hash", sourceHash);
        context.put("policy", "frozen_candidates_and_server_revalidate");
        context.set("selection_snapshot", selectionSnapshot.deepCopy());
        jdbc.update("""
                insert into fq_ai_conversation (
                    id,conversation_no,owner_user_id,customer_id,quote_id,primary_agent_id,title,channel,
                    context_json,summary_text,summary_up_to_seq,message_count,last_message_at,status,
                    create_by,create_time,update_by,update_time,row_version)
                values (:id,:no,:owner,:customer,:quote,:agent,:title,'web',:context,null,0,1,:now,'active',
                    :owner,:now,:owner,:now,1)
                """, new MapSqlParameterSource().addValue("id", conversationId)
                .addValue("no", "CONV-" + conversationId).addValue("owner", operatorId)
                .addValue("customer", quote.customerId()).addValue("quote", quote.id())
                .addValue("agent", Long.parseLong(version.agentId())).addValue("title", "选品搭配：" + quote.title())
                .addValue("context", json.write(context)).addValue("now", Timestamp.from(now)));
        jdbc.update("""
                insert into fq_ai_message (
                    id,conversation_id,seq_no,parent_message_id,run_id,role,content_text,content_json,
                    attachments_json,provider_item_id,visible_to_user,content_hash,status,error_message,
                    create_by,create_time,update_by,update_time,row_version)
                values (:id,:conversation,1,null,null,'user','请求冻结候选选品搭配',:content,json_array(),null,1,
                    :hash,'complete',null,:operator,:now,:operator,:now,1)
                """, new MapSqlParameterSource().addValue("id", messageId)
                .addValue("conversation", conversationId).addValue("content", json.write(selectionSnapshot))
                .addValue("hash", sourceHash).addValue("operator", operatorId).addValue("now", Timestamp.from(now)));
        ObjectNode budget = json.objectNode();
        budget.put("schema_version", "1.0").put("max_steps", version.maxSteps())
                .put("max_model_calls", 1).put("max_tool_calls", 0).put("max_handoffs", 0)
                .put("max_parallel", 1).put("currency", "CNY");
        jdbc.update("""
                insert into fq_ai_run (
                    id,run_no,conversation_id,agent_version_id,parent_run_id,input_message_id,trigger_type,quote_id,
                    quote_row_version,combo_id,request_key,agent_config_hash,deadline_at,budget_json,
                    context_snapshot_json,output_type,output_json,output_hash,validation_json,apply_status,
                    apply_request_key,apply_input_hash,applied_by,applied_at,applied_result_json,rejection_reason,
                    trace_id,status,current_step_no,checkpoint_json,input_tokens,cached_tokens,output_tokens,
                    reasoning_tokens,total_cost,cost_currency,run_attempt,execution_lease_id,lease_owner,fencing_token,
                    next_retry_at,lease_until,last_event_seq,started_at,finished_at,error_code,error_message,
                    create_by,create_time,update_by,update_time,row_version)
                values (:id,:no,:conversation,:version,null,:message,'style',:quote,:quoteVersion,null,
                    :requestKey,:configHash,:deadline,:budget,:snapshot,null,null,null,null,'not_applied',
                    null,null,null,null,null,null,null,'queued',0,null,null,null,null,null,null,null,0,null,null,0,
                    null,null,0,null,null,null,null,:operator,:now,:operator,:now,1)
                """, new MapSqlParameterSource().addValue("id", runId).addValue("no", "RUN-" + runId)
                .addValue("conversation", conversationId).addValue("version", Long.parseLong(version.id()))
                .addValue("message", messageId).addValue("quote", quote.id()).addValue("quoteVersion", quote.rowVersion())
                .addValue("requestKey", requestKey).addValue("configHash", version.configHash())
                .addValue("deadline", Timestamp.from(deadlineAt)).addValue("budget", json.write(budget))
                .addValue("snapshot", json.write(context)).addValue("operator", operatorId)
                .addValue("now", Timestamp.from(now)));
        return findById(runId).orElseThrow();
    }

    @Override
    public Optional<RunWorkItem> claimNext(
            long stepId, String leaseId, String owner, Instant now, Instant leaseUntil) {
        List<Long> candidates = jdbc.query("""
                select id from fq_ai_run
                 where ((status='queued' and (next_retry_at is null or next_retry_at<=:now))
                    or (status='running' and lease_until<:now))
                   and deadline_at>:now
                 order by create_time,id
                 limit 1 for update skip locked
                """, Map.of("now", Timestamp.from(now)), (rs, rowNum) -> rs.getLong(1));
        if (candidates.isEmpty()) return Optional.empty();
        long runId = candidates.get(0);
        int changed = jdbc.update("""
                update fq_ai_run set status='running',run_attempt=run_attempt+1,
                    execution_lease_id=:leaseId,lease_owner=:owner,fencing_token=fencing_token+1,
                    lease_until=:leaseUntil,next_retry_at=null,started_at=coalesce(started_at,:now),
                    current_step_no=current_step_no+1,last_event_seq=last_event_seq+1,
                    update_time=:now,row_version=row_version+1
                 where id=:id and deadline_at>:now
                """, new MapSqlParameterSource().addValue("id", runId).addValue("leaseId", leaseId)
                .addValue("owner", owner).addValue("leaseUntil", Timestamp.from(leaseUntil))
                .addValue("now", Timestamp.from(now)));
        if (changed != 1) return Optional.empty();
        RunWorkItem item = jdbc.query("""
                select r.id,r.run_no,r.trigger_type,r.conversation_id,r.agent_version_id,v.agent_id,v.provider_code,v.model_name,
                       r.agent_config_hash,r.quote_id,r.quote_row_version,q.customer_id,c.code customer_code,
                       m.content_text,r.context_snapshot_json,q.requirement_json,q.requested_qty,q.budget,q.budget_basis,q.progressive,q.combo_template_json,
                       r.request_key,r.deadline_at,r.run_attempt,r.execution_lease_id,r.lease_owner,r.fencing_token,
                       r.lease_until,r.current_step_no
                  from fq_ai_run r
                  join fq_ai_agent_version v on v.id=r.agent_version_id
                  left join fq_quote q on q.id=r.quote_id
                  left join fq_customer c on c.id=q.customer_id
                  join fq_ai_message m on m.id=r.input_message_id
                 where r.id=:id
                """, Map.of("id", runId), (rs, rowNum) -> mapWorkItem(rs, stepId)).stream().findFirst().orElseThrow();
        ObjectNode input = json.objectNode();
        if (item.quoteId() != null) input.put("quote_id", Long.toString(item.quoteId()));
        if (item.productId() != null) input.put("product_id", Long.toString(item.productId()));
        input.put("source_row_version", item.quoteId() == null ? item.productRowVersion() : item.quoteRowVersion());
        input.put("source_hash", item.sourceHash());
        String operation = "style".equals(item.triggerType()) ? "selection-styling"
                : "select_products".equals(item.triggerType()) ? "product-attribute-suggestion"
                : "requirement-analysis";
        jdbc.update("""
                insert into fq_ai_run_step (
                    id,run_id,step_no,run_attempt,execution_lease_id,fencing_token,deadline_at,parent_step_id,
                    step_type,name,call_key,attempt_no,tool_call_id,tool_name,tool_version,target_agent_id,
                    child_run_id,provider_code,external_request_key,provider_response_id,last_client_event_id,
                    event_version,input_json,input_hash,output_json,status,approval_required,approval_status,
                    approval_input_hash,approval_requested_at,approval_expires_at,approved_by,approved_at,
                    approval_reject_reason,checkpoint_json,input_tokens,output_tokens,cost,cost_currency,
                    started_at,finished_at,error_code,error_message,create_by,create_time,update_by,update_time,row_version)
                values (:id,:run,:stepNo,:attempt,:leaseId,:fence,:deadline,null,'model',:operation,
                    :callKey,:attempt,null,null,null,null,null,:provider,:externalKey,null,null,0,:input,:inputHash,null,
                    'running',0,'not_required',null,null,null,null,null,
                    null,null,null,null,null,null,:now,null,null,null,0,:now,0,:now,1)
                """, new MapSqlParameterSource().addValue("id", stepId).addValue("run", item.runId())
                .addValue("stepNo", item.stepNo()).addValue("attempt", item.runAttempt())
                .addValue("leaseId", item.leaseId()).addValue("fence", item.fencingToken())
                .addValue("deadline", Timestamp.from(item.deadlineAt())).addValue("operation", operation)
                .addValue("callKey", operation)
                .addValue("provider", item.providerCode())
                .addValue("externalKey", "run-" + item.runId() + "-attempt-" + item.runAttempt())
                .addValue("input", json.write(input)).addValue("inputHash", item.sourceHash())
                .addValue("now", Timestamp.from(now)));
        return Optional.of(item);
    }

    @Override
    public int expireDue(Instant now) {
        Map<String, Object> parameters = Map.of("now", Timestamp.from(now));
        jdbc.update("""
                update fq_ai_run_step s
                  join fq_ai_run r on r.id=s.run_id
                   set s.status=case when r.status='cancel_requested' then 'cancelled' else 'failed' end,
                       s.event_version=s.event_version+1,s.finished_at=:now,
                       s.error_code=case when r.status='cancel_requested' then 'CANCELLED' else 'DEADLINE_EXCEEDED' end,
                       s.error_message=case when r.status='cancel_requested' then '任务已取消' else '任务超过截止时间' end,
                       s.update_time=:now,s.row_version=s.row_version+1
                 where s.status='running' and (r.status='cancel_requested' or r.deadline_at<=:now)
                """, parameters);
        int cancelled = jdbc.update("""
                update fq_ai_run set status='cancelled',finished_at=:now,lease_until=null,
                    error_code='CANCELLED',error_message='任务已取消',last_event_seq=last_event_seq+1,
                    update_time=:now,row_version=row_version+1
                 where status='cancel_requested'
                """, parameters);
        int expired = jdbc.update("""
                update fq_ai_run set status='failed',finished_at=:now,lease_until=null,
                    error_code='DEADLINE_EXCEEDED',error_message='任务超过截止时间',
                    last_event_seq=last_event_seq+1,update_time=:now,row_version=row_version+1
                 where status in ('queued','running') and deadline_at<=:now
                """, parameters);
        return cancelled + expired;
    }

    @Override
    public boolean renew(long runId, String leaseId, long fencingToken, Instant now, Instant leaseUntil) {
        return jdbc.update("""
                update fq_ai_run set lease_until=:until,update_time=:now,row_version=row_version+1
                 where id=:id and status='running' and execution_lease_id=:leaseId and fencing_token=:fence
                   and lease_until>=:now and deadline_at>:now
                """, new MapSqlParameterSource().addValue("id", runId).addValue("leaseId", leaseId)
                .addValue("fence", fencingToken).addValue("until", Timestamp.from(leaseUntil))
                .addValue("now", Timestamp.from(now))) == 1;
    }

    @Override
    public boolean complete(
            RunWorkItem item, JsonNode result, String outputType, String outputHash,
            long assistantMessageId, String assistantSummary, Instant now) {
        int run = jdbc.update("""
                update fq_ai_run set output_type=:outputType,output_json=:output,output_hash=:hash,
                    validation_json=json_object('schema_version','1.0','status','valid'),status='succeeded',
                    finished_at=:now,lease_until=null,last_event_seq=last_event_seq+1,error_code=null,error_message=null,
                    update_time=:now,row_version=row_version+1
                 where id=:run and status='running' and execution_lease_id=:leaseId and fencing_token=:fence
                   and lease_until>=:now and deadline_at>:now
                """, completionParameters(item, now).addValue("output", json.write(result))
                .addValue("outputType", outputType).addValue("hash", outputHash));
        if (run != 1) return false;
        int step = jdbc.update("""
                update fq_ai_run_step set output_json=:output,status='succeeded',event_version=event_version+1,
                    finished_at=:now,update_time=:now,row_version=row_version+1
                 where id=:step and run_id=:run and status='running' and execution_lease_id=:leaseId
                   and fencing_token=:fence
                """, completionParameters(item, now).addValue("output", json.write(result)));
        if (step != 1) return false;
        jdbc.update("""
                insert into fq_ai_message (
                    id,conversation_id,seq_no,parent_message_id,run_id,role,content_text,content_json,
                    attachments_json,provider_item_id,visible_to_user,content_hash,status,error_message,
                    create_by,create_time,update_by,update_time,row_version)
                select :message,id,message_count+1,null,:run,'assistant',
                    :summary,:output,json_array(),null,1,:hash,'complete',null,
                    0,:now,0,:now,1 from fq_ai_conversation where id=:conversation
                """, new MapSqlParameterSource().addValue("message", assistantMessageId)
                .addValue("conversation", item.conversationId()).addValue("run", item.runId())
                .addValue("output", json.write(result)).addValue("hash", outputHash)
                .addValue("summary", assistantSummary)
                .addValue("now", Timestamp.from(now)));
        jdbc.update("""
                update fq_ai_conversation set message_count=message_count+1,last_message_at=:now,
                    update_time=:now,row_version=row_version+1 where id=:id
                """, Map.of("id", item.conversationId(), "now", Timestamp.from(now)));
        return true;
    }

    @Override
    public boolean fail(
            RunWorkItem item, String errorCode, String errorMessage, boolean retryable, Instant now) {
        jdbc.update("""
                update fq_ai_run_step set status='failed',event_version=event_version+1,finished_at=:now,
                    error_code=:code,error_message=:message,update_time=:now,row_version=row_version+1
                 where id=:step and run_id=:run and status='running' and execution_lease_id=:leaseId
                   and fencing_token=:fence
                """, completionParameters(item, now).addValue("code", errorCode).addValue("message", errorMessage));
        boolean scheduleRetry = retryable && item.runAttempt() < 3 && item.deadlineAt().isAfter(now.plusSeconds(2));
        return jdbc.update("""
                update fq_ai_run set status=:status,next_retry_at=:nextRetry,lease_until=null,
                    finished_at=:finished,error_code=:code,error_message=:message,last_event_seq=last_event_seq+1,
                    update_time=:now,row_version=row_version+1
                 where id=:run and status='running' and execution_lease_id=:leaseId and fencing_token=:fence
                """, completionParameters(item, now).addValue("status", scheduleRetry ? "queued" : "failed")
                .addValue("nextRetry", scheduleRetry ? Timestamp.from(now.plusSeconds(1L << item.runAttempt())) : null)
                .addValue("finished", scheduleRetry ? null : Timestamp.from(now))
                .addValue("code", errorCode).addValue("message", errorMessage)) == 1;
    }

    @Override
    public boolean acknowledgeCancellation(RunWorkItem item, Instant now) {
        int run = jdbc.update("""
                update fq_ai_run set status='cancelled',finished_at=:now,lease_until=null,
                    error_code='CANCELLED',error_message='任务已取消',last_event_seq=last_event_seq+1,
                    update_time=:now,row_version=row_version+1
                 where id=:run and status='cancel_requested' and execution_lease_id=:leaseId
                   and fencing_token=:fence
                """, completionParameters(item, now));
        if (run != 1) return false;
        jdbc.update("""
                update fq_ai_run_step set status='cancelled',event_version=event_version+1,
                    finished_at=:now,error_code='CANCELLED',error_message='任务已取消',
                    update_time=:now,row_version=row_version+1
                 where id=:step and run_id=:run and status='running' and execution_lease_id=:leaseId
                   and fencing_token=:fence
                """, completionParameters(item, now));
        return true;
    }

    @Override
    public boolean cancel(long runId, long expectedRowVersion, long operatorId, Instant now) {
        return jdbc.update("""
                update fq_ai_run set status=case when status='queued' then 'cancelled' else 'cancel_requested' end,
                    finished_at=case when status='queued' then :now else finished_at end,
                    update_by=:operator,update_time=:now,last_event_seq=last_event_seq+1,row_version=row_version+1
                 where id=:id and status in ('queued','running') and row_version=:version
                """, new MapSqlParameterSource().addValue("id", runId).addValue("version", expectedRowVersion)
                .addValue("operator", operatorId).addValue("now", Timestamp.from(now))) == 1;
    }

    @Override
    public boolean markApplied(
            long runId, String applyRequestKey, String applyInputHash, long operatorId,
            JsonNode appliedResult, Instant now) {
        Optional<RunView> existing = findById(runId);
        if (existing.isPresent() && "applied".equals(existing.get().applyStatus())) {
            String stored = jdbc.queryForObject("select apply_request_key from fq_ai_run where id=:id",
                    Map.of("id", runId), String.class);
            return applyRequestKey.equals(stored);
        }
        return jdbc.update("""
                update fq_ai_run set apply_status='applied',apply_request_key=:requestKey,
                    apply_input_hash=:inputHash,applied_by=:operator,applied_at=:now,
                    applied_result_json=:result,update_by=:operator,update_time=:now,row_version=row_version+1
                 where id=:id and status='succeeded' and apply_status='not_applied'
                """, new MapSqlParameterSource().addValue("id", runId).addValue("requestKey", applyRequestKey)
                .addValue("inputHash", applyInputHash).addValue("operator", operatorId)
                .addValue("now", Timestamp.from(now)).addValue("result", json.write(appliedResult))) == 1;
    }

    private RunWorkItem mapWorkItem(ResultSet rs, long stepId) throws SQLException {
        JsonNode snapshot = json.readTree(rs.getString("context_snapshot_json"));
        JsonNode requirement = rs.getString("requirement_json") == null
                ? json.readTree("{}") : json.readTree(rs.getString("requirement_json"));
        JsonNode product = snapshot.path("product");
        Long quoteId = nullableLong(rs, "quote_id");
        Long customerId = nullableLong(rs, "customer_id");
        JsonNode productIdNode = snapshot.path("product_id");
        Long productId = productIdNode.canConvertToLong() ? Long.valueOf(productIdNode.asLong()) :
                productIdNode.isTextual() && productIdNode.asText().matches("[1-9][0-9]{0,18}")
                        ? Long.valueOf(productIdNode.asText()) : null;
        return new RunWorkItem(rs.getLong("id"), stepId, rs.getString("run_no"), rs.getString("trigger_type"),
                rs.getLong("conversation_id"), rs.getLong("agent_version_id"), rs.getLong("agent_id"),
                rs.getString("provider_code"), rs.getString("model_name"), rs.getString("agent_config_hash"),
                quoteId, nullableLong(rs, "quote_row_version") == null ? 0 : rs.getLong("quote_row_version"), customerId,
                rs.getString("customer_code"), rs.getString("content_text"), snapshot.path("source_hash").asText(), requirement,
                rs.getInt("requested_qty"), rs.getBigDecimal("budget"), rs.getString("budget_basis"),
                rs.getInt("progressive") == 1,
                rs.getString("combo_template_json") == null ? json.readTree("{}") : json.readTree(rs.getString("combo_template_json")),
                snapshot, productId, snapshot.path("product_row_version").asLong(0), product,
                rs.getString("request_key"),
                rs.getTimestamp("deadline_at").toInstant(), rs.getInt("run_attempt"),
                rs.getString("execution_lease_id"), rs.getString("lease_owner"), rs.getLong("fencing_token"),
                rs.getTimestamp("lease_until").toInstant(), rs.getInt("current_step_no"));
    }

    private RunView mapRun(ResultSet rs, int rowNum) throws SQLException {
        Long quoteId = nullableLong(rs, "quote_id");
        Long quoteVersion = nullableLong(rs, "quote_row_version");
        return new RunView(Long.toString(rs.getLong("id")), rs.getString("run_no"),
                Long.toString(rs.getLong("conversation_id")), Long.toString(rs.getLong("agent_version_id")),
                quoteId == null ? null : Long.toString(quoteId), quoteVersion == null ? 0 : quoteVersion,
                rs.getString("request_key"), rs.getString("agent_config_hash"), instant(rs, "deadline_at"),
                json.readTree(rs.getString("context_snapshot_json")), rs.getString("output_type"),
                json.readTree(rs.getString("output_json")), rs.getString("output_hash"),
                json.readTree(rs.getString("validation_json")), rs.getString("apply_status"), rs.getString("status"),
                rs.getInt("current_step_no"), rs.getInt("run_attempt"), rs.getString("execution_lease_id"),
                rs.getString("lease_owner"), rs.getLong("fencing_token"), instant(rs, "next_retry_at"),
                instant(rs, "lease_until"), instant(rs, "started_at"), instant(rs, "finished_at"),
                rs.getString("error_code"), rs.getString("error_message"), rs.getLong("row_version"));
    }

    private MapSqlParameterSource completionParameters(RunWorkItem item, Instant now) {
        return new MapSqlParameterSource().addValue("run", item.runId()).addValue("step", item.stepId())
                .addValue("leaseId", item.leaseId()).addValue("fence", item.fencingToken())
                .addValue("now", Timestamp.from(now));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static void putNullable(ObjectNode node, String name, String value) {
        if (value == null || value.isBlank()) node.putNull(name); else node.put(name, value);
    }
}
