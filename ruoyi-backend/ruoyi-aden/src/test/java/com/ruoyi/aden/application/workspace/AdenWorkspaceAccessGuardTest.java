package com.ruoyi.aden.application.workspace;

import com.ruoyi.aden.application.error.AdenAccessDeniedException;
import com.ruoyi.aden.application.error.AdenNotFoundException;
import com.ruoyi.aden.application.security.AdenOperatorPrincipal;
import com.ruoyi.aden.domain.workspace.AdenWorkspace;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceAudit;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceForbiddenAudit;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceMemberStatus;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceMembership;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceRepository;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceRole;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdenWorkspaceAccessGuardTest {
    private static final AdenWorkspaceId WORKSPACE_ID =
            new AdenWorkspaceId("11111111-1111-4111-8111-111111111111");
    private static final String CORRELATION_ID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";

    @Test
    void wildcardPermissionDoesNotBypassMissingMembership() {
        RecordingSecurityAudit audit = new RecordingSecurityAudit();
        AdenWorkspaceAccessGuard guard = new AdenWorkspaceAccessGuard(
                new FixedRepository(Optional.empty()), audit);
        AdenOperatorPrincipal superAdmin = new AdenOperatorPrincipal(1, "admin", Set.of("*:*:*"));

        assertThrows(AdenNotFoundException.class, () -> guard.requireWorkspace(
                superAdmin, "aden:workspace:list", WORKSPACE_ID,
                AdenWorkspaceAccessGuard.READ_ROLES, CORRELATION_ID));
        assertEquals(1, audit.count);
        assertEquals(WORKSPACE_ID, audit.requestedWorkspaceId);
    }

    @Test
    void membershipRoleIsIndependentFromFeaturePermission() {
        AdenWorkspaceMembership viewer = membership(AdenWorkspaceRole.VIEWER);
        AdenWorkspaceAccessGuard guard = new AdenWorkspaceAccessGuard(
                new FixedRepository(Optional.of(viewer)), new RecordingSecurityAudit());
        AdenOperatorPrincipal principal = new AdenOperatorPrincipal(
                2, "viewer", Set.of("aden:task:create"));

        assertThrows(AdenAccessDeniedException.class, () -> guard.requireWorkspace(
                principal, "aden:task:create", WORKSPACE_ID,
                AdenWorkspaceAccessGuard.WRITE_ROLES, CORRELATION_ID));
    }

    @Test
    void resourceFromAnotherWorkspaceIsIndistinguishableFromNotFound() {
        RecordingSecurityAudit audit = new RecordingSecurityAudit();
        AdenWorkspaceAccessGuard guard = new AdenWorkspaceAccessGuard(
                new FixedRepository(Optional.empty()), audit);
        AdenWorkspaceId another = new AdenWorkspaceId("22222222-2222-4222-8222-222222222222");
        AdenOperatorPrincipal principal = new AdenOperatorPrincipal(2, "operator", Set.of("aden:task:read"));

        assertThrows(AdenNotFoundException.class, () -> guard.requireResourceWorkspace(
                principal, WORKSPACE_ID, another, CORRELATION_ID));
        assertEquals(1, audit.count);
        assertEquals(WORKSPACE_ID, audit.requestedWorkspaceId);
    }

    @Test
    void auditFailureDoesNotChangeTheExternal404Semantic() {
        AdenWorkspaceAccessGuard guard = new AdenWorkspaceAccessGuard(
                new FixedRepository(Optional.empty()),
                (principal, workspaceId, correlationId) -> {
                    throw new IllegalStateException("synthetic audit outage");
                });
        AdenOperatorPrincipal principal = new AdenOperatorPrincipal(2, "operator", Set.of("aden:task:read"));

        assertThrows(AdenNotFoundException.class, () -> guard.requireWorkspace(
                principal, "aden:task:read", WORKSPACE_ID,
                AdenWorkspaceAccessGuard.READ_ROLES, CORRELATION_ID));
    }

    @ParameterizedTest(name = "{0} 执行 {1} -> {2}")
    @MethodSource("roleMatrix")
    void fixedRoleMatrixIsEnforced(AdenWorkspaceRole role, String permission,
                                   Set<AdenWorkspaceRole> allowedRoles, boolean allowed) {
        AdenWorkspaceAccessGuard guard = new AdenWorkspaceAccessGuard(
                new FixedRepository(Optional.of(membership(role))), new RecordingSecurityAudit());
        AdenOperatorPrincipal principal = new AdenOperatorPrincipal(2, role.name(), Set.of(permission));

        if (allowed) {
            assertDoesNotThrow(() -> guard.requireWorkspace(
                    principal, permission, WORKSPACE_ID, allowedRoles, CORRELATION_ID));
        } else {
            assertThrows(AdenAccessDeniedException.class, () -> guard.requireWorkspace(
                    principal, permission, WORKSPACE_ID, allowedRoles, CORRELATION_ID));
        }
    }

    private static Stream<Arguments> roleMatrix() {
        return Stream.of(AdenWorkspaceRole.values()).flatMap(role -> Stream.of(
                Arguments.of(role, "aden:task:read", AdenWorkspaceAccessGuard.READ_ROLES, true),
                Arguments.of(role, "aden:task:create", AdenWorkspaceAccessGuard.WRITE_ROLES,
                        role != AdenWorkspaceRole.VIEWER),
                Arguments.of(role, "aden:runner:enroll", AdenWorkspaceAccessGuard.OWNER_ONLY,
                        role == AdenWorkspaceRole.OWNER)));
    }

    private static AdenWorkspaceMembership membership(AdenWorkspaceRole role) {
        Instant now = Instant.parse("2026-09-13T01:40:00Z");
        AdenWorkspace workspace = new AdenWorkspace(
                WORKSPACE_ID, "工作区", AdenWorkspaceStatus.ACTIVE, 0, 2, now, now);
        return new AdenWorkspaceMembership(
                workspace, 2, role, AdenWorkspaceMemberStatus.ACTIVE, now);
    }

    private record FixedRepository(Optional<AdenWorkspaceMembership> membership)
            implements AdenWorkspaceRepository {
        @Override
        public List<AdenWorkspaceMembership> findActiveByUserId(long ruoYiUserId, int limit) {
            return membership.stream().toList();
        }

        @Override
        public Optional<AdenWorkspaceMembership> findActiveMembership(AdenWorkspaceId workspaceId,
                                                                       long ruoYiUserId) {
            return membership.filter(value -> value.workspace().id().equals(workspaceId)
                    && value.ruoYiUserId() == ruoYiUserId);
        }

        @Override public void insertWorkspace(AdenWorkspace workspace) { throw new UnsupportedOperationException(); }
        @Override public void insertInitialOwner(AdenWorkspaceMembership value) { throw new UnsupportedOperationException(); }
        @Override public void insertAudit(AdenWorkspaceAudit audit) { throw new UnsupportedOperationException(); }
        @Override public void insertWorkspaceForbiddenAudit(AdenWorkspaceForbiddenAudit audit) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingSecurityAudit implements AdenWorkspaceSecurityAuditPort {
        private int count;
        private AdenWorkspaceId requestedWorkspaceId;

        @Override
        public void recordForbidden(AdenOperatorPrincipal principal,
                                    AdenWorkspaceId workspaceId,
                                    String correlationId) {
            count++;
            requestedWorkspaceId = workspaceId;
            assertEquals(CORRELATION_ID, correlationId);
            assertTrue(principal.userId() == 1L || principal.userId() == 2L);
        }
    }
}
