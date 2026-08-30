package com.ruoyi.interview.configuration;

import com.ruoyi.interview.application.catalog.DraftQuestion;
import com.ruoyi.interview.application.catalog.DraftQuestionVersion;
import com.ruoyi.interview.application.catalog.DraftRubric;
import com.ruoyi.interview.application.catalog.PublishQuestion;
import com.ruoyi.interview.application.catalog.QueryAdminCatalog;
import com.ruoyi.interview.application.catalog.RejectQuestionReview;
import com.ruoyi.interview.application.catalog.RetireQuestion;
import com.ruoyi.interview.application.catalog.SearchPublishedQuestions;
import com.ruoyi.interview.application.catalog.SubmitQuestionForReview;
import com.ruoyi.interview.application.catalog.internal.CatalogAccess;
import com.ruoyi.interview.application.catalog.internal.DefaultDraftQuestion;
import com.ruoyi.interview.application.catalog.internal.DefaultDraftQuestionVersion;
import com.ruoyi.interview.application.catalog.internal.DefaultDraftRubric;
import com.ruoyi.interview.application.catalog.internal.DefaultPublishQuestion;
import com.ruoyi.interview.application.catalog.internal.DefaultQueryAdminCatalog;
import com.ruoyi.interview.application.catalog.internal.DefaultRejectQuestionReview;
import com.ruoyi.interview.application.catalog.internal.DefaultRetireQuestion;
import com.ruoyi.interview.application.catalog.internal.DefaultSearchPublishedQuestions;
import com.ruoyi.interview.application.catalog.internal.DefaultSubmitQuestionForReview;
import com.ruoyi.interview.application.catalog.port.CatalogRepository;
import com.ruoyi.interview.application.catalog.port.VerifiedContentSourcePort;
import com.ruoyi.interview.application.platform.IdempotencyGuard;
import com.ruoyi.interview.application.platform.port.DomainEventPort;
import com.ruoyi.interview.application.platform.port.IdGeneratorPort;
import com.ruoyi.interview.application.platform.port.TransactionPort;
import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.catalog.port.PublishedQuestionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.ruoyi.interview.infrastructure.persistence.catalog.JdbcVerifiedContentSourceAdapter;

/** Catalog 公开只读查询的组合根绑定。管理写入工作流仍保持独立开关。 */
@Configuration
public class CatalogUseCaseConfiguration {

    @Bean
    @ConditionalOnMissingBean(VerifiedContentSourcePort.class)
    VerifiedContentSourcePort verifiedContentSourcePort(
            @Qualifier("interviewJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        // Source registry 是唯一 verified 事实源；查询不到 VERIFIED 版本时保持 fail-closed。
        return new JdbcVerifiedContentSourceAdapter(jdbc);
    }

    @Bean
    CatalogAccess catalogAccess(ActivePrincipalGuard principal) {
        return new CatalogAccess(principal);
    }
    @Bean
    @ConditionalOnMissingBean(SearchPublishedQuestions.class)
    SearchPublishedQuestions searchPublishedQuestions(PublishedQuestionPort publishedQuestions) {
        return new DefaultSearchPublishedQuestions(publishedQuestions);
    }

    @Bean
    DraftQuestion draftQuestion(CatalogRepository repository, VerifiedContentSourcePort contentSources,
                                CatalogAccess access, IdGeneratorPort ids, IdempotencyGuard idempotency,
                                DomainEventPort events, TransactionPort transaction) {
        return new DefaultDraftQuestion(repository, contentSources, access, ids, idempotency, events, transaction);
    }

    @Bean
    DraftQuestionVersion draftQuestionVersion(CatalogRepository repository,
                                              VerifiedContentSourcePort contentSources,
                                              CatalogAccess access, IdGeneratorPort ids,
                                              IdempotencyGuard idempotency, DomainEventPort events,
                                              TransactionPort transaction) {
        return new DefaultDraftQuestionVersion(repository, contentSources, access, ids, idempotency, events,
                transaction);
    }

    @Bean
    DraftRubric draftRubric(CatalogRepository repository, CatalogAccess access, IdGeneratorPort ids,
                            IdempotencyGuard idempotency, DomainEventPort events, TransactionPort transaction) {
        return new DefaultDraftRubric(repository, access, ids, idempotency, events, transaction);
    }

    @Bean
    SubmitQuestionForReview submitQuestionForReview(CatalogRepository repository, CatalogAccess access,
                                                    IdempotencyGuard idempotency, DomainEventPort events,
                                                    TransactionPort transaction) {
        return new DefaultSubmitQuestionForReview(repository, access, idempotency, events, transaction);
    }

    @Bean
    RejectQuestionReview rejectQuestionReview(CatalogRepository repository, CatalogAccess access,
                                               IdempotencyGuard idempotency, DomainEventPort events,
                                               TransactionPort transaction) {
        return new DefaultRejectQuestionReview(repository, access, idempotency, events, transaction);
    }

    @Bean
    PublishQuestion publishQuestion(CatalogRepository repository, VerifiedContentSourcePort contentSources,
                                    CatalogAccess access, IdGeneratorPort ids, IdempotencyGuard idempotency, DomainEventPort events,
                                    TransactionPort transaction) {
        return new DefaultPublishQuestion(repository, contentSources, access, ids, idempotency, events, transaction);
    }

    @Bean
    RetireQuestion retireQuestion(CatalogRepository repository, CatalogAccess access,
                                  IdempotencyGuard idempotency, DomainEventPort events,
                                  TransactionPort transaction) {
        return new DefaultRetireQuestion(repository, access, idempotency, events, transaction);
    }

    @Bean
    QueryAdminCatalog queryAdminCatalog(CatalogRepository repository, CatalogAccess access) {
        return new DefaultQueryAdminCatalog(repository, access);
    }
}

