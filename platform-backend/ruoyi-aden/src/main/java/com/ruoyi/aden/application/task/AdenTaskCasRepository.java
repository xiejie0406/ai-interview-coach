package com.ruoyi.aden.application.task;

import com.ruoyi.aden.domain.task.AdenTask;

public interface AdenTaskCasRepository {
    void updateState(AdenTask previous, AdenTask next);
}
