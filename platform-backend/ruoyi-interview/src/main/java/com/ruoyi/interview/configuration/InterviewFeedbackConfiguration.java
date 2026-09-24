package com.ruoyi.interview.configuration;
import com.ruoyi.interview.application.evaluation.*;
import com.ruoyi.interview.application.interview.port.InterviewRepository;
import com.ruoyi.interview.application.catalog.port.PublishedQuestionPort;
import com.ruoyi.interview.application.security.ActivePrincipalGuard;
import com.ruoyi.interview.application.governance.port.ConsentQueryPort;
import com.ruoyi.interview.application.agent.port.ChatModelPort;
import com.ruoyi.interview.configuration.properties.ProviderProperties;
import com.ruoyi.interview.domain.platform.TenantId;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
@Configuration
public class InterviewFeedbackConfiguration {
    @Bean
    InterviewFeedbackService interviewFeedbackService(InterviewRepository sessions,PublishedQuestionPort catalog,
            InterviewFeedbackStore store,ActivePrincipalGuard guard,ConsentQueryPort consents,ChatModelPort model,
            ProviderProperties properties,Environment environment) {
        return new InterviewFeedbackService(sessions,catalog,store,guard,consents,model,
                TenantId.of(environment.getRequiredProperty("interview.catalog.public-tenant-id")),properties.getDeepseek().getEvaluationModel());
    }
}
