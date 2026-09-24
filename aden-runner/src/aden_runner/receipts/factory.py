"""把本地执行结果转换为 Runner v1 receipt 请求。"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any, Literal
from uuid import uuid4

from aden_runner.lease import LeaseLedger

ReceiptType = Literal[
    "STARTED",
    "PROGRESS",
    "SUCCEEDED",
    "FAILED_RETRYABLE",
    "FAILED_FINAL",
    "CANCELED_AT_SAFE_POINT",
    "OUTCOME_UNKNOWN",
]


class ReceiptFactory:
    def __init__(self, ledger: LeaseLedger) -> None:
        self._ledger = ledger
        self._pending: dict[str, PendingReceipt] = {}

    def build(
        self,
        delivery_id: str,
        session_epoch: int,
        receipt_type: ReceiptType,
        payload: dict[str, Any],
    ) -> tuple[str, dict[str, Any]]:
        pending = self._pending.get(delivery_id)
        if pending is not None:
            if pending.receipt_type != receipt_type:
                raise RuntimeError("上一条 receipt 尚未确认，拒绝生成下一条")
            return pending.idempotency_key, dict(pending.body)
        state = self._ledger.require_live(delivery_id)
        sequence = self._ledger.next_receipt(delivery_id)
        receipt_id = str(uuid4())
        idempotency_key = f"receipt:{receipt_id}"
        body = {
            "deliveryId": delivery_id,
            "receiptId": receipt_id,
            "sessionEpoch": str(session_epoch),
            "fencingToken": str(state.delivery.fence_token),
            "receiptSequence": str(sequence),
            "kind": receipt_type,
            "observedAt": datetime.now(UTC).isoformat(),
            "data": payload,
        }
        self._pending[delivery_id] = PendingReceipt(
            idempotency_key, receipt_type, dict(payload), dict(body)
        )
        return idempotency_key, body

    def acknowledge(self, delivery_id: str, idempotency_key: str) -> None:
        pending = self._pending.get(delivery_id)
        if pending is None or pending.idempotency_key != idempotency_key:
            raise RuntimeError("receipt 确认与待发送记录不匹配")
        self._pending.pop(delivery_id)

    def clear(self) -> None:
        self._pending.clear()


@dataclass(frozen=True)
class PendingReceipt:
    idempotency_key: str
    receipt_type: ReceiptType
    payload: dict[str, Any]
    body: dict[str, Any]
