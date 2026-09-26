package com.ruoyi.aden.api.stream;

import com.ruoyi.aden.api.common.AdenCorrelationIdFilter;
import com.ruoyi.aden.application.projection.AdenOperatorQueryService;
import com.ruoyi.aden.application.security.AdenOperatorPrincipalProvider;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.infrastructure.stream.AdenWorkspaceEventBroadcaster;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequestMapping("/api/v1/aden/workspaces/{workspaceId}/events")
@ConditionalOnProperty(prefix = "aden", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdenEventStreamController {
    private final AdenOperatorPrincipalProvider principals;
    private final AdenWorkspaceEventBroadcaster broadcaster;

    public AdenEventStreamController(AdenOperatorPrincipalProvider principals,
                                     AdenWorkspaceEventBroadcaster broadcaster) {
        this.principals = principals;
        this.broadcaster = broadcaster;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.hasPermi('aden:event:subscribe')")
    public ResponseEntity<SseEmitter> subscribe(
            @PathVariable String workspaceId,
            @RequestHeader(value = "Last-Event-ID", required = false)
            @Size(min = 24, max = 768) String cursor,
            @RequestParam(defaultValue = AdenOperatorQueryService.STREAM_FILTER) String filter,
            HttpServletRequest request) {
        if (!AdenOperatorQueryService.STREAM_FILTER.equals(filter)) {
            throw new IllegalArgumentException("filter 只允许 workspace-all-v1");
        }
        AdenWorkspaceId workspace = new AdenWorkspaceId(workspaceId);
        SseEmitter emitter = broadcaster.open(principals.current(), workspace, cursor,
                AdenCorrelationIdFilter.correlationId(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no").body(emitter);
    }
}
