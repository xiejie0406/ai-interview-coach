"""只解释冻结 TaskPackage 的合成执行器。"""

from __future__ import annotations

import hashlib
import json
from typing import Any, cast

from aden_runner.protocol.models import Delivery


class PackageIntegrityError(RuntimeError):
    pass


class FixtureExecutor:
    def execute(self, delivery: Delivery) -> dict[str, Any]:
        raw_package: object = json.loads(delivery.task_package_json)
        if not isinstance(raw_package, dict):
            raise PackageIntegrityError("TaskPackage 必须是 JSON object")
        package = cast(dict[str, object], raw_package)
        embedded_hash = package.pop("packageHash", None)
        canonical = json.dumps(package, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
        calculated = hashlib.sha256(canonical.encode()).hexdigest()
        if embedded_hash != delivery.task_package_hash or calculated != delivery.task_package_hash:
            raise PackageIntegrityError("TaskPackage hash 不一致")
        if package.get("schemaVersion") != 1 or package.get("externalActionsEnabled") is not False:
            raise PackageIntegrityError("TaskPackage schema 或副作用门禁非法")
        raw_input = package.get("input")
        if not isinstance(raw_input, dict):
            raise PackageIntegrityError("TaskPackage fixture 非法")
        input_value = cast(dict[str, object], raw_input)
        fixture_id = input_value.get("fixtureId")
        if not isinstance(fixture_id, str) or not fixture_id.startswith("fixture:"):
            raise PackageIntegrityError("TaskPackage fixture 非法")
        return {
            "fixtureId": fixture_id,
            "result": "SYNTHETIC_OK",
            "externalActionsPerformed": False,
        }
