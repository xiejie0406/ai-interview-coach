"""合成 Runner 的 fence、heartbeat 与 receipt 单调序号账本。"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime

from aden_runner.protocol.models import Delivery


class LeaseLost(RuntimeError):
    pass


@dataclass
class LeaseState:
    delivery: Delivery
    heartbeat_sequence: int = 0
    receipt_sequence: int = 0
    cancel_requested: bool = False


class LeaseLedger:
    def __init__(self) -> None:
        self._leases: dict[str, LeaseState] = {}

    def accept(self, delivery: Delivery) -> LeaseState:
        current = self._leases.get(delivery.delivery_id)
        if current is not None and delivery.fence_token < current.delivery.fence_token:
            raise LeaseLost("拒绝旧 fence Delivery")
        if current is not None and delivery.fence_token == current.delivery.fence_token:
            return current
        state = LeaseState(delivery=delivery)
        self._leases[delivery.delivery_id] = state
        return state

    def require_live(self, delivery_id: str, now: datetime | None = None) -> LeaseState:
        state = self._leases.get(delivery_id)
        if state is None:
            raise LeaseLost("Delivery 不在本地租约账本")
        instant = now or datetime.now(UTC)
        if state.delivery.lease_until <= instant:
            self._leases.pop(delivery_id, None)
            raise LeaseLost("Delivery lease 已过期")
        return state

    def next_heartbeat(self, delivery_id: str) -> int:
        state = self.require_live(delivery_id)
        state.heartbeat_sequence += 1
        return state.heartbeat_sequence

    def next_receipt(self, delivery_id: str) -> int:
        state = self.require_live(delivery_id)
        state.receipt_sequence += 1
        return state.receipt_sequence

    def request_cancel(self, delivery_id: str) -> None:
        self.require_live(delivery_id).cancel_requested = True

    def finish(self, delivery_id: str) -> None:
        self._leases.pop(delivery_id, None)

    def clear(self) -> None:
        self._leases.clear()

    def active(self) -> tuple[LeaseState, ...]:
        return tuple(self._leases[key] for key in sorted(self._leases))
