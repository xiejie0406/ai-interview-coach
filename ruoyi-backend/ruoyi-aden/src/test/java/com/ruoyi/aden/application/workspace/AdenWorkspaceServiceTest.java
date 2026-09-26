package com.ruoyi.aden.application.workspace;

import com.ruoyi.aden.application.error.AdenAccessDeniedException;
import com.ruoyi.aden.application.security.AdenOperatorPrincipal;
import com.ruoyi.aden.domain.shared.AdenIdGenerator;
import com.ruoyi.aden.domain.workspace.AdenWorkspace;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceAudit;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceForbiddenAudit;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceMembership;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdenWorkspaceServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-13T01:40:00Z");

    @Test
    void createWritesWorkspaceOwnerAndAuditWithServerOwnedActor() {
        RecordingRepository repository = new RecordingRepository();
        AdenWorkspaceService service = new AdenWorkspaceService(
                repository,
                sequentialIds(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        AdenOperatorPrincipal principal = new AdenOperatorPrincipal(
                42, "operator", Set.of(AdenWorkspaceService.CREATE_PERMISSION));

        AdenWorkspaceMembership result = service.create(principal, "  我的工作区  ",
                "cccccccc-cccc-4ccc-8ccc-cccccccccccc");

        assertEquals("11111111-1111-4111-8111-111111111111", result.workspace().id().value());
        assertEquals("我的工作区", result.workspace().displayName());
        assertEquals(42, result.ruoYiUserId());
        assertEquals("OWNER", result.role().name());
        assertEquals(List.of("workspace", "owner", "audit"), repository.operations);
        assertEquals(42, repository.audit.actorUserId());
        assertEquals(result.workspace().id(), repository.audit.workspaceId());
        assertEquals(NOW, repository.audit.occurredAt());
    }

    @Test
    void applicationLayerRejectsMissingPermissionEvenWithoutControllerAdvice() {
        AdenWorkspaceService service = new AdenWorkspaceService(
                new RecordingRepository(), sequentialIds(), Clock.fixed(NOW, ZoneOffset.UTC));
        AdenOperatorPrincipal principal = new AdenOperatorPrincipal(42, "operator", Set.of());

        assertThrows(AdenAccessDeniedException.class,
                () -> service.create(principal, "工作区", "cccccccc-cccc-4ccc-8ccc-cccccccccccc"));
    }

    @Test
    void listIsBoundToAuthenticatedUserAndHardMaximum() {
        RecordingRepository repository = new RecordingRepository();
        AdenWorkspaceService service = new AdenWorkspaceService(
                repository, sequentialIds(), Clock.fixed(NOW, ZoneOffset.UTC));

        service.list(new AdenOperatorPrincipal(77, "viewer", Set.of(AdenWorkspaceService.LIST_PERMISSION)));

        assertEquals(77, repository.listUserId);
        assertEquals(100, repository.listLimit);
    }

    private static AdenIdGenerator sequentialIds() {
        List<String> ids = new ArrayList<>(List.of(
                "11111111-1111-4111-8111-111111111111",
                "22222222-2222-4222-8222-222222222222"));
        return () -> ids.remove(0);
    }

    private static final class RecordingRepository implements AdenWorkspaceRepository {
        private final List<String> operations = new ArrayList<>();
        private AdenWorkspaceAudit audit;
        private long listUserId;
        private int listLimit;

        @Override
        public List<AdenWorkspaceMembership> findActiveByUserId(long ruoYiUserId, int limit) {
            listUserId = ruoYiUserId;
            listLimit = limit;
            return List.of();
        }

        @Override
        public Optional<AdenWorkspaceMembership> findActiveMembership(AdenWorkspaceId workspaceId,
                                                                       long ruoYiUserId) {
            return Optional.empty();
        }

        @Override
        public void insertWorkspace(AdenWorkspace workspace) {
            operations.add("workspace");
        }

        @Override
        public void insertInitialOwner(AdenWorkspaceMembership membership) {
            operations.add("owner");
        }

        @Override
        public void insertAudit(AdenWorkspaceAudit value) {
            operations.add("audit");
            audit = value;
        }

        @Override
        public void insertWorkspaceForbiddenAudit(AdenWorkspaceForbiddenAudit audit) {
            operations.add("forbidden-audit");
        }
    }
}
