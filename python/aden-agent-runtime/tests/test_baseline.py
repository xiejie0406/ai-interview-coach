from __future__ import annotations

import ast
from pathlib import Path

import pytest
from pydantic import ValidationError

from aden_agent_runtime.config import AgentRuntimeBaselineSettings
from aden_agent_runtime.contracts import AgentJob
from aden_agent_runtime.model_ports import (
    DisabledModelPort,
    FakeModelPort,
    ModelAgentPort,
    ProviderDisabledError,
)

SOURCE_ROOT = Path(__file__).resolve().parents[1] / "src"
FORBIDDEN_IMPORT_ROOTS = {
    "aiohttp",
    "ctypes",
    "httpx",
    "playwright",
    "pyautogui",
    "requests",
    "selenium",
    "socket",
    "subprocess",
    "urllib3",
}


def test_agent_baseline_is_provider_disabled() -> None:
    settings = AgentRuntimeBaselineSettings()

    assert settings.provider_disabled is True
    assert settings.transport == "fake"
    assert settings.outbound_network == "disabled"


@pytest.mark.parametrize(
    "override",
    [
        {"provider_disabled": False},
        {"transport": "http"},
        {"outbound_network": "enabled"},
        {"shell_enabled": True},
        {"external_write_enabled": True},
    ],
)
def test_agent_baseline_rejects_capability_enablement(override: dict[str, object]) -> None:
    with pytest.raises(ValidationError):
        AgentRuntimeBaselineSettings.model_validate(override)


def test_fake_model_port_is_deterministic(job: AgentJob) -> None:
    port: ModelAgentPort = FakeModelPort()

    first = port.generate(job, frozenset())
    second = port.generate(job, frozenset())

    assert first == second
    assert first.kind == "PROPOSAL"


def test_disabled_model_port_fails_closed(job: AgentJob) -> None:
    port: ModelAgentPort = DisabledModelPort()

    with pytest.raises(ProviderDisabledError, match="provider_disabled"):
        port.generate(job, frozenset())


def test_agent_source_has_no_forbidden_runtime_imports() -> None:
    violations: list[str] = []
    for source_path in SOURCE_ROOT.rglob("*.py"):
        tree = ast.parse(source_path.read_text(encoding="utf-8"), filename=str(source_path))
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    if alias.name.split(".", maxsplit=1)[0] in FORBIDDEN_IMPORT_ROOTS:
                        violations.append(f"{source_path}:{node.lineno}:{alias.name}")
            elif (
                isinstance(node, ast.ImportFrom)
                and node.module
                and node.module.split(".", maxsplit=1)[0] in FORBIDDEN_IMPORT_ROOTS
            ):
                violations.append(f"{source_path}:{node.lineno}:{node.module}")

    assert violations == []
