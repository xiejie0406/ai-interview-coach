package com.aiinterviewcoach.adapters.inbound.rest.health;

import com.aiinterviewcoach.adapters.inbound.rest.RestInboundAdapter;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/health", produces = MediaType.APPLICATION_JSON_VALUE)
public class HealthController implements RestInboundAdapter {
    private final ServiceMetadata serviceMetadata;

    public HealthController(ServiceMetadata serviceMetadata) {
        this.serviceMetadata = serviceMetadata;
    }

    @GetMapping
    public ResponseEntity<ServiceHealth> health() {
        ServiceHealth health = new ServiceHealth(
                "UP",
                serviceMetadata.serviceName(),
                serviceMetadata.releaseVersion());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(health);
    }

    public record ServiceHealth(String status, String service, String version) {
    }
}
