package com.ruoyi.interview.controller;

import java.util.Map;
import com.ruoyi.common.core.domain.AjaxResult;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.ruoyi.interview.configuration.RuoYiPrincipalFacade;

/** 语音同意的 RuoYi 入口；没有 PostgreSQL repository 时明确返回未就绪。 */
@RestController
@ConditionalOnProperty(prefix = "interview.foundation-safety", name = "business-rest-endpoints-enabled",
        havingValue = "false", matchIfMissing = true)
@RequestMapping(path = "/api/v1/consents", produces = MediaType.APPLICATION_JSON_VALUE)
public class ConsentController {

    private final RuoYiPrincipalFacade principals;

    public ConsentController(RuoYiPrincipalFacade principals) {
        this.principals = principals;
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('interview:voice:upload')")
    public ResponseEntity<AjaxResult> current() {
        principals.requiredPrincipal();
        AjaxResult result = AjaxResult.error(HttpStatus.NOT_IMPLEMENTED.value(), "语音同意记录持久化尚未就绪");
        result.put("errorCode", "VOICE_NOT_READY");
        result.put("retryable", false);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(result);
    }
}
