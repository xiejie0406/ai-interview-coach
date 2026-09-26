package com.ruoyi.aps.solver.ortools;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 Java 包、当前平台 JNI 和最小 CP-SAT 模型可以协同工作。
 */
class OrToolsNativeSmokeTest
{
    @Test
    void loadsNativeLibraryAndSolvesMinimalModel()
    {
        Loader.loadNativeLibraries();

        CpModel model = new CpModel();
        IntVar decision = model.newIntVar(0, 1, "decision");
        model.maximize(decision);

        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(model);

        assertEquals(CpSolverStatus.OPTIMAL, status);
        assertEquals(1L, solver.value(decision));
    }
}
