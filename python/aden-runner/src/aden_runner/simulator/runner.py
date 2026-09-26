"""Session → claim → heartbeat → receipt 的确定性单轮模拟器。"""

from __future__ import annotations

import hashlib
import json
from datetime import UTC, datetime
from enum import StrEnum
from uuid import uuid4

from aden_runner.fixtures import FixtureExecutor
from aden_runner.lease import LeaseLedger
from aden_runner.protocol.models import SessionResponse
from aden_runner.receipts import ReceiptFactory
from aden_runner.receipts.factory import ReceiptType
from aden_runner.transport import AdenTransport


class FaultPoint(StrEnum):
    NONE = "none"
    AFTER_CLAIM = "after_claim"
    AFTER_STARTED = "after_started"
    BEFORE_FINAL = "before_final"


class SimulatedCrash(RuntimeError):
    pass


class RunnerSimulator:
    def __init__(self, transport: AdenTransport) -> None:
        self._transport = transport
        self._ledger = LeaseLedger()
        self._receipts = ReceiptFactory(self._ledger)
        self._executor = FixtureExecutor()
        self._session: SessionResponse | None = None
        self._session_heartbeat_sequence = 0

    def exchange_session(self) -> SessionResponse:
        self._ledger.clear()
        self._receipts.clear()
        self._session_heartbeat_sequence = 0
        self._session = self._transport.exchange_session()
        return self._session

    def run_once(
        self, fault: FaultPoint = FaultPoint.NONE, claim_request_id: str | None = None
    ) -> int:
        session = self._require_session()
        claim_id = claim_request_id or str(uuid4())
        claim = self._transport.claim(session.session_token, claim_id)
        for delivery in claim.deliveries:
            self._ledger.accept(delivery)
        self._crash(fault, FaultPoint.AFTER_CLAIM)

        completed = 0
        for delivery in claim.deliveries:
            self._send_receipt(
                delivery.delivery_id,
                "STARTED",
                {"startedAt": datetime.now(UTC).isoformat()},
            )
            self._crash(fault, FaultPoint.AFTER_STARTED)
            self._session_heartbeat_sequence += 1
            session_heartbeat = self._transport.heartbeat_session(
                session.session_token,
                session.session_id,
                session.session_epoch,
                self._session_heartbeat_sequence,
                datetime.now(UTC).isoformat(),
                [delivery.delivery_id],
            )
            delivery_heartbeat = self._transport.heartbeat_delivery(
                session.session_token,
                delivery.delivery_id,
                session.session_epoch,
                delivery.fence_token,
                self._ledger.next_heartbeat(delivery.delivery_id),
                datetime.now(UTC).isoformat(),
            )
            cancel = (
                delivery.delivery_id in session_heartbeat.cancel_delivery_ids
                or delivery_heartbeat.cancel_requested
            )
            if cancel:
                self._ledger.request_cancel(delivery.delivery_id)
                self._send_receipt(
                    delivery.delivery_id,
                    "CANCELED_AT_SAFE_POINT",
                    {
                        "safePoint": "before-fixture",
                        "finishedAt": datetime.now(UTC).isoformat(),
                    },
                )
            else:
                result = self._executor.execute(delivery)
                self._send_receipt(
                    delivery.delivery_id,
                    "PROGRESS",
                    {"progressPercent": 100, "message": "合成 fixture 已完成"},
                )
                self._crash(fault, FaultPoint.BEFORE_FINAL)
                result_hash = hashlib.sha256(
                    json.dumps(
                        result,
                        ensure_ascii=False,
                        separators=(",", ":"),
                        sort_keys=True,
                    ).encode()
                ).hexdigest()
                self._send_receipt(
                    delivery.delivery_id,
                    "SUCCEEDED",
                    {
                        "resultHash": result_hash,
                        "finishedAt": datetime.now(UTC).isoformat(),
                        "summary": "合成执行完成，未产生外部副作用。",
                    },
                )
            self._ledger.finish(delivery.delivery_id)
            completed += 1
        return completed

    def _send_receipt(
        self, delivery_id: str, receipt_type: ReceiptType, payload: dict[str, object]
    ) -> None:
        session = self._require_session()
        allowed = {
            "STARTED",
            "PROGRESS",
            "SUCCEEDED",
            "FAILED_RETRYABLE",
            "FAILED_FINAL",
            "CANCELED_AT_SAFE_POINT",
            "OUTCOME_UNKNOWN",
        }
        if receipt_type not in allowed:
            raise ValueError("未知 receipt type")
        key, body = self._receipts.build(
            delivery_id, session.session_epoch, receipt_type, payload
        )
        self._transport.receipt(session.session_token, key, body)
        self._receipts.acknowledge(delivery_id, key)

    def _require_session(self) -> SessionResponse:
        if self._session is None:
            raise RuntimeError("必须先 exchange Session")
        return self._session

    @staticmethod
    def _crash(actual: FaultPoint, expected: FaultPoint) -> None:
        if actual == expected:
            raise SimulatedCrash(expected.value)
