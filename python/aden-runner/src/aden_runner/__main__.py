"""Runner 基线自检与显式 loopback 单轮模拟入口。"""

from __future__ import annotations

import argparse
import os

from aden_runner.config import RunnerBaselineSettings, RunnerSettings
from aden_runner.simulator import RunnerSimulator
from aden_runner.transport import AdenTransport


def main() -> int:
    """默认只自检；仅显式 --simulator-once 才访问受限 loopback RuoYi。"""

    parser = argparse.ArgumentParser(description="Aden low-privilege runner")
    parser.add_argument("--simulator-once", action="store_true")
    args = parser.parse_args()
    if args.simulator_once:
        settings = RunnerSettings(
            origin=_required_env("ADEN_RUNNER_ORIGIN"),
            credential_token=_required_env("ADEN_RUNNER_CREDENTIAL_TOKEN"),
        )
        simulator = RunnerSimulator(AdenTransport(settings))
        simulator.exchange_session()
        completed = simulator.run_once()
        print(f'{{"mode":"simulator-once","completed":{completed}}}')
        return 0

    settings = RunnerBaselineSettings()
    print(settings.model_dump_json())
    return 0


def _required_env(name: str) -> str:
    value = os.environ.get(name, "")
    if not value:
        raise RuntimeError(f"缺少必填环境变量 {name}")
    return value


if __name__ == "__main__":
    raise SystemExit(main())
