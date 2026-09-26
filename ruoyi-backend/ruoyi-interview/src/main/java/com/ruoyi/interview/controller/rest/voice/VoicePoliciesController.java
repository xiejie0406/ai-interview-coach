package com.ruoyi.interview.controller.rest.voice;

import com.ruoyi.interview.configuration.InterviewEnabled;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 同意界面与授权用例共享同一服务端政策版本目录。 */
@InterviewEnabled
@RestController
@PreAuthorize("@ss.hasPermi('interview:voice:upload')")
@ConditionalOnProperty(prefix = "interview.foundation-safety", name = "business-rest-endpoints-enabled", havingValue = "true")
public class VoicePoliciesController {
    @GetMapping("/api/v1/policies/current")
    public Policies current() {
        return new Policies(VoicePolicyCatalog.POLICIES.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().name()))
                .map(entry -> new Policy(entry.getKey().name(), entry.getValue().resourceId().value(), false)).toList());
    }
    public record Policies(List<Policy> policies) { }
    public record Policy(String purpose, String versionId, boolean requiredForRegistration) { }
}
