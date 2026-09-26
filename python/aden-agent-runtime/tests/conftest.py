from __future__ import annotations

from pathlib import Path

import pytest

from aden_agent_runtime.contracts import AgentJob


@pytest.fixture
def job() -> AgentJob:
    path = Path(__file__).parent / "fixtures" / "agent-job.json"
    return AgentJob.model_validate_json(path.read_text(encoding="utf-8"))
