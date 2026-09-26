package com.ruoyi.interview.controller.rest.interview;

import com.ruoyi.interview.configuration.InterviewEnabled;
import com.ruoyi.interview.application.evaluation.*;
import com.ruoyi.interview.controller.rest.common.RequestContextFactory;
import com.ruoyi.interview.domain.platform.ResourceId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@InterviewEnabled
@RestController
@ConditionalOnProperty(prefix="interview.foundation-safety",name="business-rest-endpoints-enabled",havingValue="true")
@RequestMapping("/api/v1/interviews/{interviewId}/feedback")
public class InterviewFeedbackController {
    private final InterviewFeedbackService service;
    private final RequestContextFactory contexts;
    public InterviewFeedbackController(InterviewFeedbackService service,RequestContextFactory contexts) { this.service=service; this.contexts=contexts; }
    @GetMapping
    @PreAuthorize("@ss.hasPermi('interview:session:recover')")
    public InterviewFeedback get(@PathVariable UUID interviewId,HttpServletRequest request) {
        return service.get(contexts.query(request).principal(),ResourceId.of(interviewId));
    }
    @PostMapping
    @PreAuthorize("@ss.hasPermi('interview:session:submit')")
    public InterviewFeedback generate(@PathVariable UUID interviewId,HttpServletRequest request) {
        return service.generate(ResourceId.of(interviewId),contexts.operation(request));
    }
}
