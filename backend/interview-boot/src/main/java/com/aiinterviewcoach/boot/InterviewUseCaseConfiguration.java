package com.aiinterviewcoach.boot;

import com.aiinterviewcoach.adapters.outbound.persistence.shared.JdbcPersistenceSupport;
import com.aiinterviewcoach.application.billing.CheckEntitlement;
import com.aiinterviewcoach.application.billing.ReleaseUsage;
import com.aiinterviewcoach.application.billing.ReserveUsage;
import com.aiinterviewcoach.application.billing.internal.DefaultCheckEntitlement;
import com.aiinterviewcoach.application.billing.internal.DefaultEntitlementPort;
import com.aiinterviewcoach.application.billing.internal.DefaultReleaseUsage;
import com.aiinterviewcoach.application.billing.internal.DefaultReserveUsage;
import com.aiinterviewcoach.application.billing.port.BillingRepository;
import com.aiinterviewcoach.application.billing.port.EntitlementPort;
import com.aiinterviewcoach.application.identity.ActivePrincipalGuard;
import com.aiinterviewcoach.application.interview.ConfirmInterviewPlan;
import com.aiinterviewcoach.application.interview.CreateInterviewPlan;
import com.aiinterviewcoach.application.interview.CreateInterviewSession;
import com.aiinterviewcoach.application.interview.RecoverInterview;
import com.aiinterviewcoach.application.interview.ProgressInterview;
import com.aiinterviewcoach.application.interview.StartInterview;
import com.aiinterviewcoach.application.interview.SubmitInterviewAnswer;
import com.aiinterviewcoach.application.interview.ApplySessionCommand;
import com.aiinterviewcoach.application.interview.internal.DefaultConfirmInterviewPlan;
import com.aiinterviewcoach.application.interview.internal.DefaultCreateInterviewPlan;
import com.aiinterviewcoach.application.interview.internal.DefaultCreateInterviewSession;
import com.aiinterviewcoach.application.interview.internal.DefaultRecoverInterview;
import com.aiinterviewcoach.application.interview.internal.DefaultProgressInterview;
import com.aiinterviewcoach.application.interview.internal.DefaultStartInterview;
import com.aiinterviewcoach.application.interview.internal.DefaultSubmitInterviewAnswer;
import com.aiinterviewcoach.application.interview.internal.DefaultApplySessionCommand;
import com.aiinterviewcoach.application.interview.internal.PlanUsageEstimator;
import com.aiinterviewcoach.application.interview.internal.ProfileAccessPort;
import com.aiinterviewcoach.application.interview.port.InterviewPlanPolicyPort;
import com.aiinterviewcoach.application.interview.port.InterviewRecoveryProjectionPort;
import com.aiinterviewcoach.application.interview.port.InterviewRepository;
import com.aiinterviewcoach.application.interview.port.PlanQuestionSelectionPort;
import com.aiinterviewcoach.application.platform.IdempotencyGuard;
import com.aiinterviewcoach.application.platform.port.ClockPort;
import com.aiinterviewcoach.application.platform.port.DomainEventPort;
import com.aiinterviewcoach.application.platform.port.IdGeneratorPort;
import com.aiinterviewcoach.application.platform.port.JobPort;
import com.aiinterviewcoach.application.platform.port.TransactionPort;
import com.aiinterviewcoach.domain.interview.PlannedQuestion;
import com.aiinterviewcoach.domain.interview.UsageEstimate;
import com.aiinterviewcoach.domain.platform.ImmutableVersionRef;
import com.aiinterviewcoach.domain.platform.ResourceId;
import com.aiinterviewcoach.domain.platform.TenantId;
import com.aiinterviewcoach.application.catalog.port.PublishedQuestionPort;
import com.aiinterviewcoach.application.interview.InterviewAgentCandidate;
import com.aiinterviewcoach.application.interview.internal.DefaultInterviewAgentCandidate;
import com.aiinterviewcoach.application.agent.port.ChatModelPort;
import com.aiinterviewcoach.application.voice.port.ContentDigestPort;
import com.aiinterviewcoach.boot.properties.ProviderProperties;
import com.aiinterviewcoach.domain.platform.TimeBudget;
import com.aiinterviewcoach.domain.platform.UsageQuantity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 迁移阶段的 Interview/Billing 组合根；所有写入仍通过既有 application use case。 */
@Configuration
public class InterviewUseCaseConfiguration {

    @Bean
    CheckEntitlement checkEntitlement(BillingRepository repository, ActivePrincipalGuard principal) {
        return new DefaultCheckEntitlement(repository, principal);
    }

    @Bean
    ReserveUsage reserveUsage(BillingRepository repository, ActivePrincipalGuard principal,
                              IdGeneratorPort ids, IdempotencyGuard idempotency,
                              DomainEventPort events, TransactionPort transaction) {
        return new DefaultReserveUsage(repository, principal, ids, idempotency, events, transaction);
    }

    @Bean
    ReleaseUsage releaseUsage(BillingRepository repository, ActivePrincipalGuard principal,
                              IdempotencyGuard idempotency, DomainEventPort events,
                              TransactionPort transaction) {
        return new DefaultReleaseUsage(repository, principal, idempotency, events, transaction);
    }

    @Bean
    EntitlementPort entitlementPort(CheckEntitlement check, ReserveUsage reserve, ReleaseUsage release,
                                    BillingRepository repository, ActivePrincipalGuard principal, ClockPort clock) {
        return new DefaultEntitlementPort(check, reserve, release, repository, principal, clock);
    }

    @Bean
    InterviewPlanPolicyPort interviewPlanPolicy(NamedParameterJdbcTemplate jdbc) {
        return request -> {
            ImmutableVersionRef profile = jdbc.query("""
                    select profile_version_id, version_no, content_hash
                      from identity.profile_version
                     where tenant_id = :tenantId and user_id = :userId
                     order by version_no desc limit 1
                    """, Map.of("tenantId", request.tenantId().value(), "userId", request.userId().value()),
                    (row, index) -> new ImmutableVersionRef(ResourceId.of(row.getString("profile_version_id")),
                            row.getInt("version_no"), row.getString("content_hash")))
                    .stream().findFirst().orElseThrow(() -> new IllegalStateException("当前账号缺少有效面试档案"));

            // 本地迁移验收的试用权益。生产套餐接入后由 Billing onboarding 取代此幂等初始化。
            jdbc.update("""
                    insert into billing.entitlement (
                        tenant_id, entitlement_id, user_id, product_plan_id, source,
                        valid_from, valid_to, usage_unit, limit_value, consumed_value,
                        reserved_value, state, aggregate_version)
                    select :tenantId, :entitlementId, :userId, :productPlanId, 'TRIAL',
                           :validFrom, :validTo, 'INTERVIEW_CREDIT', 10000, 0, 0, 'ACTIVE', 0
                     where not exists (
                        select 1 from billing.entitlement
                         where tenant_id = :tenantId and user_id = :userId
                           and usage_unit = 'INTERVIEW_CREDIT' and state = 'ACTIVE')
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", request.tenantId().value())
                    .addValue("userId", request.userId().value())
                    .addValue("entitlementId", UUID.randomUUID().toString())
                    .addValue("productPlanId", "local-migration-trial")
                    .addValue("validFrom", JdbcPersistenceSupport.writeInstant(
                            request.requestedAt().minus(Duration.ofDays(1))))
                    .addValue("validTo", JdbcPersistenceSupport.writeInstant(
                            request.requestedAt().plus(Duration.ofDays(365)))));

            int questionCount = Math.max(1, Math.min(6, request.durationMinutes() / 5));
            return new InterviewPlanPolicyPort.Resolved(
                    "migration-policy-v1", profile, request.topics(),
                    java.util.Set.of(request.targetRole().name()),
                    java.util.Set.of(request.targetLevel().name()),
                    new TimeBudget(Duration.ofMinutes(request.durationMinutes())),
                    questionCount, Math.min(2, questionCount), request.requestedAt().plus(Duration.ofHours(2)));
        };
    }

    @Bean
    PlanQuestionSelectionPort planQuestionSelection(NamedParameterJdbcTemplate jdbc, Environment environment) {
        String publicTenantId = environment.getRequiredProperty("interview.catalog.public-tenant-id");
        return request -> {
            long millis = Math.max(60_000L,
                    request.totalTimeBudget().duration().toMillis() / request.desiredQuestionCount());
            String topic = request.topicCodes().stream().sorted().findFirst().orElse("GENERAL");
            List<PlannedQuestion> selected = jdbc.query("""
                    select p.question_version_id, p.question_version_no, p.question_content_hash,
                           p.rubric_version_id, p.rubric_version_no, p.rubric_content_hash
                      from catalog.question_publication p
                      join catalog.question_version qv
                        on qv.tenant_id = p.tenant_id and qv.question_version_id = p.question_version_id
                     where p.tenant_id = :catalogTenantId
                       and exists (select 1 from jsonb_array_elements_text(qv.target_roles) r(value)
                                    where r.value in (:roles))
                     order by p.question_id
                     limit :limit
                    """, new MapSqlParameterSource()
                    .addValue("catalogTenantId", publicTenantId)
                    .addValue("roles", request.targetRoles())
                    .addValue("limit", request.desiredQuestionCount()),
                    (row, index) -> new PlannedQuestion(index + 1,
                            new ImmutableVersionRef(ResourceId.of(row.getString("question_version_id")),
                                    row.getInt("question_version_no"), row.getString("question_content_hash")),
                            new ImmutableVersionRef(ResourceId.of(row.getString("rubric_version_id")),
                                    row.getInt("rubric_version_no"), row.getString("rubric_content_hash")),
                            topic, new TimeBudget(Duration.ofMillis(millis)),
                            index < request.totalFollowUpBudget() ? 1 : 0));
            if (selected.isEmpty()) {
                throw new IllegalStateException("公开题库中没有匹配当前岗位的已发布题目");
            }
            return selected;
        };
    }

    @Bean
    PlanUsageEstimator planUsageEstimator() {
        return (questions, followUps) -> new UsageEstimate(
                new UsageQuantity("INTERVIEW_CREDIT", BigDecimal.valueOf(questions.size())),
                "migration-credit-v1");
    }

    @Bean
    CreateInterviewPlan createInterviewPlan(InterviewRepository repository, PlanQuestionSelectionPort selection,
                                            InterviewPlanPolicyPort policy, ProfileAccessPort profiles,
                                            PlanUsageEstimator usage, ActivePrincipalGuard principal,
                                            IdGeneratorPort ids, IdempotencyGuard idempotency,
                                            DomainEventPort events, TransactionPort transaction) {
        return new DefaultCreateInterviewPlan(repository, selection, policy, profiles, usage,
                principal, ids, idempotency, events, transaction);
    }

    @Bean
    ConfirmInterviewPlan confirmInterviewPlan(InterviewRepository repository, EntitlementPort billing,
                                              ActivePrincipalGuard principal, IdempotencyGuard idempotency,
                                              DomainEventPort events, TransactionPort transaction) {
        return new DefaultConfirmInterviewPlan(repository, billing, principal, idempotency, events, transaction);
    }

    @Bean
    CreateInterviewSession createInterviewSession(InterviewRepository repository, EntitlementPort billing,
                                                  ActivePrincipalGuard principal, IdGeneratorPort ids,
                                                  IdempotencyGuard idempotency, DomainEventPort events,
                                                  TransactionPort transaction,
                                                  InterviewRecoveryProjectionPort projections) {
        return new DefaultCreateInterviewSession(repository, billing, principal, ids, idempotency,
                events, transaction, projections);
    }

    @Bean
    RecoverInterview recoverInterview(InterviewRepository repository, ActivePrincipalGuard principal,
                                      InterviewRecoveryProjectionPort projections, TransactionPort transaction) {
        return new DefaultRecoverInterview(repository, principal, projections, transaction);
    }

    @Bean
    StartInterview startInterview(InterviewRepository repository, EntitlementPort billing, JobPort jobs,
                                  ActivePrincipalGuard principal, IdGeneratorPort ids,
                                  IdempotencyGuard idempotency, DomainEventPort events,
                                  TransactionPort transaction, InterviewRecoveryProjectionPort projections) {
        return new DefaultStartInterview(repository, billing, jobs, principal, ids, idempotency,
                events, transaction, projections);
    }

    @Bean
    SubmitInterviewAnswer submitInterviewAnswer(InterviewRepository repository, JobPort jobs,
                                                ActivePrincipalGuard principal, IdGeneratorPort ids,
                                                IdempotencyGuard idempotency, DomainEventPort events,
                                                TransactionPort transaction,
                                                InterviewRecoveryProjectionPort projections) {
        return new DefaultSubmitInterviewAnswer(repository, jobs, principal, ids, idempotency,
                events, transaction, projections);
    }

    @Bean
    ApplySessionCommand applySessionCommand(InterviewRepository repository, ActivePrincipalGuard principal,
                                            IdempotencyGuard idempotency, DomainEventPort events,
                                            TransactionPort transaction,
                                            InterviewRecoveryProjectionPort projections) {
        return new DefaultApplySessionCommand(repository, principal, idempotency, events,
                transaction, projections);
    }

    @Bean
    InterviewAgentCandidate interviewAgentCandidate(ChatModelPort model, IdGeneratorPort ids,
                                                     ProviderProperties providers) {
        return new DefaultInterviewAgentCandidate(model, ids,
                providers.getDeepseek().getInterviewModel());
    }

    @Bean
    ProgressInterview progressInterview(InterviewRepository repository, PublishedQuestionPort questions,
                                        ActivePrincipalGuard principal, IdGeneratorPort ids,
                                        DomainEventPort events, TransactionPort transaction,
                                        InterviewRecoveryProjectionPort projections, Environment environment,
                                        InterviewAgentCandidate agent, ContentDigestPort digest) {
        return new DefaultProgressInterview(repository, questions,
                TenantId.of(environment.getRequiredProperty("interview.catalog.public-tenant-id")),
                principal, ids, events, transaction, projections, agent, digest);
    }
}
