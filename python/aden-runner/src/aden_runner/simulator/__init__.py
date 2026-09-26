"""无权限、无外部副作用的 Runner 协议模拟器。"""

from aden_runner.simulator.runner import FaultPoint, RunnerSimulator, SimulatedCrash

__all__ = ["FaultPoint", "RunnerSimulator", "SimulatedCrash"]
