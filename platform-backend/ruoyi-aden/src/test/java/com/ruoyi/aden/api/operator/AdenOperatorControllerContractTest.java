package com.ruoyi.aden.api.operator;

import com.ruoyi.aden.api.common.AdenCorrelationIdFilter;
import com.ruoyi.aden.api.stream.AdenEventStreamController;
import com.ruoyi.aden.application.error.AdenApplicationException;
import com.ruoyi.aden.application.projection.AdenOperatorQueryService;
import com.ruoyi.aden.application.runner.AdenRunnerAdministrationService;
import com.ruoyi.aden.application.security.AdenOperatorPrincipal;
import com.ruoyi.aden.application.security.AdenOperatorPrincipalProvider;
import com.ruoyi.aden.application.task.AdenSyntheticTaskInput;
import com.ruoyi.aden.application.task.AdenTaskCommandService;
import com.ruoyi.aden.application.task.AdenTaskResult;
import com.ruoyi.aden.application.task.AdenTaskTransactionService;
import com.ruoyi.aden.contract.OperatorTaskCommand;
import com.ruoyi.aden.domain.task.AdenCapabilityCode;
import com.ruoyi.aden.domain.task.AdenTaskId;
import com.ruoyi.aden.domain.task.AdenTaskState;
import com.ruoyi.aden.domain.task.AdenTaskVersion;
import com.ruoyi.aden.domain.workspace.AdenWorkspaceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdenOperatorControllerContractTest {
    private static final String WORKSPACE = "11111111-1111-4111-8111-111111111111";
    private static final String TASK = "22222222-2222-4222-8222-222222222222";
    private static final String CORRELATION = "77777777-7777-4777-8777-777777777777";
    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

    private AdenOperatorPrincipalProvider principals;
    private AdenOperatorQueryService queries;
    private AdenTaskCommandService tasks;
    private AdenOperatorController controller;

    @BeforeEach
    void createController() {
        principals = mock(AdenOperatorPrincipalProvider.class);
        queries = mock(AdenOperatorQueryService.class);
        tasks = mock(AdenTaskCommandService.class);
        var runners = mock(AdenRunnerAdministrationService.class);
        when(principals.current()).thenReturn(new AdenOperatorPrincipal(
                42, "operator", Set.of("aden:task:create", "aden:task:command", "aden:task:cancel")));
        controller = new AdenOperatorController(principals, queries, tasks, runners);
    }

    @Test
    void routeSurfaceMatchesCanonicalOperatorAndStreamV1() {
        assertEquals(List.of("/api/v1/aden/workspaces/{workspaceId}"), List.of(
                AdenOperatorController.class.getAnnotation(RequestMapping.class).value()));
        assertEquals(Set.of("/bootstrap", "/capabilities", "/tasks", "/tasks/{taskId}",
                        "/runners", "/audit-events"),
                mappings(AdenOperatorController.class, GetMapping.class));
        assertEquals(Set.of("/tasks", "/tasks/{taskId}/commands", "/runners:enroll",
                        "/runners/{runnerId}:revoke"),
                mappings(AdenOperatorController.class, PostMapping.class));
        assertEquals(List.of("/api/v1/aden/workspaces/{workspaceId}/events"), List.of(
                AdenEventStreamController.class.getAnnotation(RequestMapping.class).value()));
        assertEquals(1, Arrays.stream(AdenEventStreamController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)).count());
    }

    @Test
    void createReturns201LocationAndStrongAggregateEtagWithoutInt64PrecisionLoss() {
        AdenWorkspaceId workspaceId = new AdenWorkspaceId(WORKSPACE);
        when(tasks.createTask(any())).thenReturn(new AdenTaskResult(workspaceId,
                new AdenTaskId(TASK), AdenTaskState.DRAFT, new AdenTaskVersion(Long.MAX_VALUE), null, false));
        when(queries.getTaskAfterWrite(any(), any(), any(), any(), any())).thenReturn(snapshot(Long.MAX_VALUE));

        var response = controller.createTask(WORKSPACE, "create:key-1",
                new AdenOperatorController.CreateTaskRequest("SYNTHETIC_CORE", AdenCapabilityCode.CORE,
                        "合成任务", new AdenOperatorController.SyntheticInput(
                        "fixture:core-success-v1", "只运行合成 fixture",
                        AdenSyntheticTaskInput.ExpectedOutcome.SUCCEED)), request());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/v1/aden/workspaces/" + WORKSPACE + "/tasks/" + TASK,
                response.getHeaders().getLocation().toString());
        assertEquals("\"task-" + TASK + "-v9223372036854775807\"", response.getHeaders().getETag());
        assertNotNull(response.getBody());
        assertEquals("9223372036854775807", response.getBody().version());
    }

    @Test
    void commandUnionRejectsMissingOrExtraneousReasonBeforeMutation() {
        var cancelWithoutReason = new AdenOperatorController.TaskCommandRequest(
                OperatorTaskCommand.REQUEST_CANCEL, null);
        var submitWithReason = new AdenOperatorController.TaskCommandRequest(
                OperatorTaskCommand.SUBMIT_FOR_VALIDATION, "NOT_ALLOWED");

        assertThrows(IllegalArgumentException.class, () -> controller.command(
                WORKSPACE, TASK, "cancel:key", "\"task-" + TASK + "-v1\"",
                cancelWithoutReason, request()));
        assertThrows(IllegalArgumentException.class, () -> controller.command(
                WORKSPACE, TASK, "submit:key", "\"task-" + TASK + "-v1\"",
                submitWithReason, request()));
        verify(tasks, never()).requestCancel(any());
        verify(tasks, never()).submitForValidation(any());
    }

    @Test
    void missingIfMatchMapsToStablePreconditionRequiredCode() {
        AdenApplicationException failure = assertThrows(AdenApplicationException.class,
                () -> controller.command(WORKSPACE, TASK, "submit:key", null,
                        new AdenOperatorController.TaskCommandRequest(
                                OperatorTaskCommand.SUBMIT_FOR_VALIDATION, null), request()));

        assertEquals("ADEN_PRECONDITION_REQUIRED", failure.errorCode());
        verify(tasks, never()).submitForValidation(any());
    }

    private static <A extends java.lang.annotation.Annotation> Set<String> mappings(
            Class<?> type, Class<A> annotationType) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(annotationType))
                .map(method -> {
                    if (annotationType == GetMapping.class) {
                        return method.getAnnotation(GetMapping.class).value()[0];
                    }
                    return method.getAnnotation(PostMapping.class).value()[0];
                }).collect(Collectors.toSet());
    }

    private static AdenOperatorQueryService.TaskSnapshot snapshot(long version) {
        return new AdenOperatorQueryService.TaskSnapshot(TASK, WORKSPACE, "SYNTHETIC_CORE", "CORE",
                "合成任务", "DRAFT", Long.toString(version),
                List.of(OperatorTaskCommand.SUBMIT_FOR_VALIDATION), List.of(), null, NOW, NOW, CORRELATION);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdenCorrelationIdFilter.REQUEST_ATTRIBUTE, CORRELATION);
        return request;
    }
}
