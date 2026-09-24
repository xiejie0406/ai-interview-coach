"""Aden Runner v1 HTTP 客户端；禁用环境代理与重定向。"""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any
from uuid import uuid4

import httpx

from aden_runner.config import RunnerSettings
from aden_runner.protocol.models import (
    ClaimResponse,
    DeliveryHeartbeatResponse,
    ReceiptResponse,
    SessionHeartbeatResponse,
    SessionResponse,
)


class AdenTransport:
    def __init__(self, settings: RunnerSettings, client: httpx.Client | None = None) -> None:
        self._settings = settings
        self._client = client or httpx.Client(
            base_url=settings.origin,
            follow_redirects=False,
            trust_env=False,
            timeout=httpx.Timeout(10.0),
        )

    def exchange_session(self) -> SessionResponse:
        response = self._post(
            "/api/v1/aden/runner/v1/sessions",
            headers={"Authorization": f"AdenCredential {self._settings.credential_token}"},
            json={
                "protocolVersion": self._settings.protocol_version,
                "runnerVersion": self._settings.runner_version,
                "capacity": self._settings.capacity,
                "capabilities": ["CORE"],
            },
        )
        return SessionResponse.model_validate(response.json())

    def claim(self, session_token: str, claim_request_id: str) -> ClaimResponse:
        response = self._post(
            "/api/v1/aden/runner/v1/deliveries:claim",
            headers=self._session_headers(session_token, f"claim:{claim_request_id}"),
            json={
                "claimRequestId": claim_request_id,
                "capabilities": ["CORE"],
                "capacity": self._settings.capacity,
                "batchLimit": self._settings.batch_limit,
            },
        )
        return ClaimResponse.model_validate(response.json())

    def heartbeat_session(
        self,
        session_token: str,
        session_id: str,
        session_epoch: int,
        heartbeat_sequence: int,
        observed_at: str,
        active_delivery_ids: list[str],
    ) -> SessionHeartbeatResponse:
        response = self._post(
            f"/api/v1/aden/runner/v1/sessions/{session_id}/heartbeats",
            headers=self._session_headers(session_token),
            json={
                "sessionEpoch": str(session_epoch),
                "heartbeatSequence": str(heartbeat_sequence),
                "observedAt": observed_at,
                "activeDeliveryIds": active_delivery_ids,
            },
        )
        return SessionHeartbeatResponse.model_validate(response.json())

    def heartbeat_delivery(
        self,
        session_token: str,
        delivery_id: str,
        session_epoch: int,
        fence_token: int,
        heartbeat_sequence: int,
        observed_at: str,
    ) -> DeliveryHeartbeatResponse:
        response = self._post(
            f"/api/v1/aden/runner/v1/deliveries/{delivery_id}/heartbeats",
            headers=self._session_headers(session_token),
            json={
                "sessionEpoch": str(session_epoch),
                "fencingToken": str(fence_token),
                "heartbeatSequence": str(heartbeat_sequence),
                "observedAt": observed_at,
            },
        )
        return DeliveryHeartbeatResponse.model_validate(response.json())

    def receipt(
        self,
        session_token: str,
        idempotency_key: str,
        body: Mapping[str, Any],
    ) -> ReceiptResponse:
        response = self._post(
            f"/api/v1/aden/runner/v1/deliveries/{body['deliveryId']}/receipts",
            headers=self._session_headers(session_token, idempotency_key),
            json={key: value for key, value in body.items() if key != "deliveryId"},
        )
        return ReceiptResponse.model_validate(response.json())

    def _session_headers(self, token: str, idempotency_key: str | None = None) -> dict[str, str]:
        headers = {
            "Authorization": f"AdenRunner {token}",
            "X-Correlation-ID": str(uuid4()),
        }
        if idempotency_key is not None:
            headers["Idempotency-Key"] = idempotency_key
        return headers

    def _post(self, path: str, **kwargs: Any) -> httpx.Response:
        request = self._client.build_request("POST", self._settings.origin + path, **kwargs)
        request_origin = str(request.url.copy_with(path="/", query=None, fragment=None))
        RunnerSettings.loopback_origin_only(request_origin)
        response = self._client.send(request)
        if response.is_redirect:
            raise RuntimeError("Runner 拒绝 HTTP redirect")
        response.raise_for_status()
        return response
