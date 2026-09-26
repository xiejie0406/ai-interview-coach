package com.ruoyi.aps.api.controller;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import com.ruoyi.aps.application.foundation.ApsBusinessException;
import com.ruoyi.aps.application.foundation.ApsErrorCode;
import com.ruoyi.aps.solver.contract.SolverInput;
import com.ruoyi.aps.solver.contract.SolverInputCodec;
import com.ruoyi.aps.solver.contract.ValidationResult;
import com.ruoyi.aps.validator.IndependentConstraintValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SolverInput v1 的无副作用确定性校验入口。 */
@RestController
@RequestMapping("/api/aps/v1/validations")
@ConditionalOnProperty(prefix = "aps", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.api", name = "enabled", havingValue = "true")
public class ApsValidationController
{
    private final SolverInputCodec codec = new SolverInputCodec();
    private final IndependentConstraintValidator validator = new IndependentConstraintValidator();
    private final Clock clock;

    public ApsValidationController() { this(Clock.systemUTC()); }
    ApsValidationController(Clock clock) { this.clock = clock; }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('aps:planning:validate')")
    public ValidationResult validate(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody String body)
    {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128)
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "Idempotency-Key 必须是 1 至 128 位非空文本");
        byte[] json = body.getBytes(StandardCharsets.UTF_8);
        try
        {
            SolverInput input = codec.decode(json);
            return validator.validateInput(input, json, Instant.now(clock));
        }
        catch (RuntimeException exception)
        {
            if (exception instanceof ApsBusinessException businessException) throw businessException;
            throw new ApsBusinessException(ApsErrorCode.INVALID_REQUEST, "请求不符合 SolverInput v1 结构契约");
        }
    }
}
