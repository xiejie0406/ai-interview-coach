"""不记录 prompt、证据全文、凭据或隐藏推理。"""

from __future__ import annotations

import re

type JsonValue = str | int | bool | list[JsonValue] | dict[str, JsonValue] | None
_SENSITIVE_KEY = re.compile(r"(?:password|secret|token|credential|api[_-]?key)", re.IGNORECASE)
_BEARER = re.compile(r"(?i)bearer\s+[A-Za-z0-9._~+/-]+=*")


def redact(value: JsonValue) -> JsonValue:
    if isinstance(value, dict):
        return {
            key: "[REDACTED]" if _SENSITIVE_KEY.search(key) else redact(nested)
            for key, nested in value.items()
        }
    if isinstance(value, list):
        return [redact(item) for item in value]
    if isinstance(value, str):
        return _BEARER.sub("Bearer [REDACTED]", value)
    return value


def runtime_event(
    event: str, *, job_id: str, attempt_id: str, outcome: str
) -> dict[str, JsonValue]:
    return {
        "event": event,
        "jobId": job_id,
        "attemptId": attempt_id,
        "outcome": outcome,
    }
