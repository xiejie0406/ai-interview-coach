from __future__ import annotations

import sys

import pytest

from aden_runner import __main__


def test_default_cli_remains_offline(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setattr(sys, "argv", ["aden-runner"])
    assert __main__.main() == 0
    assert '"outbound_network":"disabled"' in capsys.readouterr().out


def test_simulator_cli_requires_explicit_loopback_configuration(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(sys, "argv", ["aden-runner", "--simulator-once"])
    monkeypatch.delenv("ADEN_RUNNER_ORIGIN", raising=False)
    monkeypatch.delenv("ADEN_RUNNER_CREDENTIAL_TOKEN", raising=False)
    with pytest.raises(RuntimeError, match="ADEN_RUNNER_ORIGIN"):
        __main__.main()
