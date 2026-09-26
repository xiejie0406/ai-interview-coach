package com.ruoyi.aden.configuration;

import com.ruoyi.aden.api.common.AdenApiExceptionHandler;
import com.ruoyi.aden.api.common.AdenNotFoundController;
import com.ruoyi.aden.api.operator.AdenOperatorController;
import com.ruoyi.aden.api.operator.WorkspaceController;
import com.ruoyi.aden.api.runner.AdenRunnerController;
import com.ruoyi.aden.api.stream.AdenEventStreamController;
import com.ruoyi.aden.infrastructure.persistence.mapper.AdenWorkspaceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AdenModuleSwitchTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AdenConfiguration.class, AdenRuntimeConfiguration.class,
                    AdenSchedulingConfiguration.class, AdenApiExceptionHandler.class,
                    AdenNotFoundController.class, AdenOperatorController.class,
                    WorkspaceController.class, AdenRunnerController.class,
                    AdenEventStreamController.class);

    @Test
    void missingSwitchKeepsAdenHttpEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(AdenNotFoundController.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AdenNotFoundController.class);
                });
    }

    @Test
    void disablingAdenRemovesHttpPersistenceGuardAndBackgroundTasks() {
        contextRunner.withPropertyValues("aden.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AdenProperties.class);
            assertThat(context).doesNotHaveBean(AdenWorkspaceMapper.class);
            assertThat(context).doesNotHaveBean("adenSchemaGuard");
            assertThat(context).doesNotHaveBean("adenMaintenanceCoordinator");
            assertThat(context).doesNotHaveBean(AdenApiExceptionHandler.class);
            assertThat(context).doesNotHaveBean(AdenNotFoundController.class);
            assertThat(context).doesNotHaveBean(AdenOperatorController.class);
            assertThat(context).doesNotHaveBean(WorkspaceController.class);
            assertThat(context).doesNotHaveBean(AdenRunnerController.class);
            assertThat(context).doesNotHaveBean(AdenEventStreamController.class);
        });
    }
}
