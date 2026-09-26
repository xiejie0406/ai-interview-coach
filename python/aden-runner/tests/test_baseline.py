from __future__ import annotations

import ast
from pathlib import Path

import pytest
from pydantic import ValidationError

from aden_runner.config import RunnerBaselineSettings

SOURCE_ROOT = Path(__file__).resolve().parents[1] / "src"
FORBIDDEN_IMPORT_ROOTS = {
    "aiohttp",
    "ctypes",
    "playwright",
    "pyautogui",
    "requests",
    "selenium",
    "socket",
    "subprocess",
    "urllib3",
}


def test_runner_baseline_is_fail_closed() -> None:
    settings = RunnerBaselineSettings()

    assert settings.outbound_network == "disabled"
    assert settings.uia_enabled is False
    assert settings.shell_enabled is False
    assert settings.browser_enabled is False
    assert settings.arbitrary_file_access_enabled is False
    assert settings.external_write_enabled is False


@pytest.mark.parametrize(
    "field",
    [
        "uia_enabled",
        "shell_enabled",
        "browser_enabled",
        "arbitrary_file_access_enabled",
        "external_write_enabled",
    ],
)
def test_runner_baseline_rejects_capability_enablement(field: str) -> None:
    with pytest.raises(ValidationError):
        RunnerBaselineSettings.model_validate({field: True})


def test_runner_source_has_no_forbidden_runtime_imports() -> None:
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
