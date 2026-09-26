from aden_agent_runtime.observability import redact, runtime_event


def test_logs_redact_credentials_and_do_not_include_context_or_prompt() -> None:
    value = redact(
        {
            "credentialToken": "sensitive",
            "message": "Authorization: Bearer abc.def.ghi",
            "safe": ["ok"],
        }
    )
    assert value == {
        "credentialToken": "[REDACTED]",
        "message": "Authorization: Bearer [REDACTED]",
        "safe": ["ok"],
    }
    event = runtime_event("attempt.completed", job_id="job", attempt_id="attempt", outcome="ok")
    assert set(event) == {"event", "jobId", "attemptId", "outcome"}
