"""离线 Agent Runtime CLI；只读取显式 fixture，不监听端口。"""

from __future__ import annotations

import argparse
from pathlib import Path

from aden_agent_runtime.bootstrap import RuntimeSettings
from aden_agent_runtime.client import FakeAgentTransport
from aden_agent_runtime.contracts import AgentJob
from aden_agent_runtime.model_ports import FakeModelPort
from aden_agent_runtime.runtime import AgentRuntime
from aden_agent_runtime.tools import ToolGateway


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Aden provider-disabled 离线 Agent Runtime")
    parser.add_argument("fixture", nargs="?", type=Path, help="本地合成 AgentJob JSON")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    settings = RuntimeSettings()
    if args.fixture is None:
        print(settings.model_dump_json(exclude={"allowed_tool_names"}))
        return 0
    job = AgentJob.model_validate_json(args.fixture.read_text(encoding="utf-8"))
    transport = FakeAgentTransport(job)
    result = AgentRuntime(
        FakeModelPort(),
        transport,
        ToolGateway(settings.allowed_tool_names),
        # 离线 CLI 使用 fixture 冻结时间以确保相同输入产生字节稳定候选
        clock=lambda: job.context_view.created_at,
    ).run(job)
    print(result.candidate.model_dump_json(by_alias=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
