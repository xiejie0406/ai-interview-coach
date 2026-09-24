from __future__ import annotations

import importlib.metadata
import json
from pathlib import Path
import platform
import subprocess
import sys
from typing import Any, Iterable

import yaml
from jsonschema import Draft202012Validator, FormatChecker
from openapi_spec_validator import validate_url
from referencing import Registry, Resource


APS_ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = APS_ROOT / "contract-test-manifest.json"
REQUIREMENTS_PATH = APS_ROOT / "requirements-contracts.txt"


def json_pointer(parts: Iterable[Any]) -> str:
    encoded = [str(part).replace("~", "~0").replace("/", "~1") for part in parts]
    return "/" + "/".join(encoded) if encoded else ""


def flatten_errors(errors: Iterable[Any]) -> list[Any]:
    flattened: list[Any] = []
    for error in errors:
        flattened.append(error)
        flattened.extend(flatten_errors(error.context))
    return flattened


def load_pinned_requirements() -> dict[str, str]:
    pinned: dict[str, str] = {}
    for line in REQUIREMENTS_PATH.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        name, separator, version = stripped.partition("==")
        if separator != "==" or not name or not version:
            raise AssertionError(f"Requirement must use exact == pin: {stripped}")
        pinned[name] = version
    return pinned


def verify_toolchain(manifest: dict[str, Any]) -> None:
    expected_python = manifest["toolchain"]["python"]
    if platform.python_version() != expected_python:
        raise AssertionError(f"Python must be {expected_python}, got {platform.python_version()}")
    for package, expected in load_pinned_requirements().items():
        actual = importlib.metadata.version(package)
        if actual != expected:
            raise AssertionError(f"{package} must be {expected}, got {actual}")
    print("Pinned Python toolchain: PASS")


def verify_schema_cases(manifest: dict[str, Any]) -> dict[str, dict[str, Any]]:
    schemas: dict[str, dict[str, Any]] = {}
    for logical_name, relative_path in manifest["schemas"].items():
        schema = json.loads((APS_ROOT / relative_path).read_text(encoding="utf-8"))
        Draft202012Validator.check_schema(schema)
        schemas[logical_name] = schema
    registry = Registry().with_resources(
        (schema["$id"], Resource.from_contents(schema)) for schema in schemas.values()
    )

    listed_instances = {case["instance"] for case in manifest["cases"]}
    actual_instances = {
        path.relative_to(APS_ROOT).as_posix()
        for path in (APS_ROOT / "examples" / "golden").glob("*.json")
    }
    if listed_instances != actual_instances:
        missing = sorted(actual_instances - listed_instances)
        stale = sorted(listed_instances - actual_instances)
        raise AssertionError(f"Manifest/example mismatch; missing={missing}, stale={stale}")

    for case in manifest["cases"]:
        instance = json.loads((APS_ROOT / case["instance"]).read_text(encoding="utf-8"))
        validator = Draft202012Validator(
            schemas[case["schema"]], registry=registry, format_checker=FormatChecker()
        )
        errors = list(validator.iter_errors(instance))
        valid = not errors
        if valid != case["expectedValid"]:
            details = [f"{json_pointer(error.absolute_path)}: {error.message}" for error in errors]
            raise AssertionError(
                f"{case['instance']}: expectedValid={case['expectedValid']}, got {valid}; {details}"
            )
        if not valid:
            pointers = {json_pointer(error.absolute_path) for error in flatten_errors(errors)}
            expected_pointers = set(case.get("expectedErrorPointers", []))
            missing_pointers = expected_pointers - pointers
            if missing_pointers:
                raise AssertionError(
                    f"{case['instance']}: expected error pointers missing {sorted(missing_pointers)}; got {sorted(pointers)}"
                )
        outcome = "SCHEMA_PASS" if valid else "EXPECTED_SCHEMA_FAIL"
        print(f"{case['instance']}: {outcome}")
    return schemas


def verify_openapi_and_cross_contracts(
    manifest: dict[str, Any], schemas: dict[str, dict[str, Any]]
) -> None:
    openapi_path = APS_ROOT / manifest["openapi"]
    validate_url(openapi_path.resolve().as_uri())
    openapi = yaml.safe_load(openapi_path.read_text(encoding="utf-8"))
    if openapi["openapi"] != "3.1.0":
        raise AssertionError("OpenAPI must be 3.1.0")

    expected_plan_statuses = {
        "DRAFT",
        "SOLVING",
        "FEASIBLE",
        "CONFLICT",
        "CANCELLED",
        "PUBLISHING",
        "PUBLISHED",
        "FAILED",
        "SUPERSEDED",
    }
    result_statuses = set(schemas["SolverResult"]["properties"]["planStatus"]["enum"])
    api_statuses = set(
        openapi["components"]["schemas"]["PlanRequestStatusResponse"]["properties"]["planStatus"]["enum"]
    )
    if result_statuses != expected_plan_statuses or api_statuses != expected_plan_statuses:
        raise AssertionError("M19 plan status sets differ across SolverResult and OpenAPI")

    expected_paths = {
        "/api/aps/v1/health",
        "/api/aps/v1/capabilities",
        "/api/aps/v1/resources/workshops",
        "/api/aps/v1/resources/workshops/{id}",
        "/api/aps/v1/resources/workshops/{workshopId}/centers",
        "/api/aps/v1/resources/centers",
        "/api/aps/v1/resources/centers/{id}",
        "/api/aps/v1/resources",
        "/api/aps/v1/resources/{id}",
        "/api/aps/v1/resources/{resourceId}/skills",
        "/api/aps/v1/resources/{resourceId}/availability",
        "/api/aps/v1/resources/{resourceId}/net-availability",
        "/api/aps/v1/resources/{resourceId}/availability-import",
        "/api/aps/v1/resources/workshops/{workshopId}/readiness",
        "/api/aps/v1/resources/workshops/{workshopId}/candidates",
        "/api/aps/v1/resources/personnel-export",
        "/api/aps/v1/routings/items",
        "/api/aps/v1/routings/items/{id}",
        "/api/aps/v1/routings/operations",
        "/api/aps/v1/routings/operations/{id}",
        "/api/aps/v1/routings/operations/{id}/activate",
        "/api/aps/v1/routings/routes",
        "/api/aps/v1/routings/routes/{id}",
        "/api/aps/v1/routings/routes/{id}/graph",
        "/api/aps/v1/routings/routes/{id}/copy",
        "/api/aps/v1/routings/routes/{id}/validation",
        "/api/aps/v1/routings/routes/{id}/publish",
        "/api/aps/v1/orders",
        "/api/aps/v1/orders/import",
        "/api/aps/v1/orders/{id}",
        "/api/aps/v1/orders/{id}/expand",
        "/api/aps/v1/orders/{id}/expansion",
        "/api/aps/v1/orders/{id}/release",
        "/api/aps/v1/orders/lots",
        "/api/aps/v1/orders/lines/{lineId}/route-migration",
        "/api/aps/v1/schemas",
        "/api/aps/v1/validations",
        "/api/aps/v1/plan-requests",
        "/api/aps/v1/plan-requests/{requestId}",
        "/api/aps/v1/plan-requests/{requestId}/cancel",
        "/api/aps/v1/plan-requests/{requestId}/events",
        "/api/aps/v1/plan-versions/{planVersionId}",
        "/api/aps/v1/plan-versions/{planVersionId}/comparison",
        "/api/aps/v1/plan-versions/{planVersionId}/adjustments",
        "/api/aps/v1/plan-versions/{planVersionId}/structural-adjustments",
        "/api/aps/v1/plan-versions/{planVersionId}/publish",
        "/api/aps/v1/plan-versions/{planVersionId}/discard",
        "/api/aps/v1/plan-versions/{planVersionId}/locks",
        "/api/aps/v1/plan-versions/{planVersionId}/locks/{lockId}",
        "/api/aps/v1/execution-runs",
        "/api/aps/v1/execution-runs/{executionRunId}",
        "/api/aps/v1/execution-runs/{executionRunId}/transitions",
        "/api/aps/v1/execution-runs/{executionRunId}/resource-changes",
        "/api/aps/v1/execution-runs/{executionRunId}/phase-advances",
        "/api/aps/v1/execution-runs/{executionRunId}/reports",
        "/api/aps/v1/production-reports/{reportId}/corrections",
        "/api/aps/v1/output-lots/{outputLotId}/quality-decisions",
        "/api/aps/v1/output-lots/{outputLotId}/quantity-movements",
        "/api/aps/v1/reports/daily-production",
        "/api/aps/v1/reports/daily-production.csv",
        "/api/aps/v1/reports/labor-capacity",
        "/api/aps/v1/reports/labor-capacity.csv",
        "/api/aps/v1/reports/order-delivery",
        "/api/aps/v1/reports/order-delivery.csv",
    }
    if set(openapi["paths"]) != expected_paths:
        raise AssertionError("OpenAPI paths differ from the approved implemented boundary")

    top_level_responses = {
        "HealthResponse": "APS_HEALTH",
        "CapabilitiesResponse": "APS_CAPABILITIES",
        "SchemaMetadataResponse": "APS_SCHEMA_METADATA",
        "PlanRequestStatusResponse": "PLAN_REQUEST_STATUS",
        "PlanVersionDetailResponse": "PLAN_VERSION_DETAIL",
        "PlanVersionComparisonResponse": "PLAN_VERSION_COMPARISON",
        "PlanAdjustmentAcceptedResponse": "PLAN_ADJUSTMENT_ACCEPTED",
        "PlanStructuralAdjustmentAcceptedResponse": "PLAN_STRUCTURAL_ADJUSTMENT_ACCEPTED",
        "PlanPublishedResponse": "PLAN_PUBLISHED",
        "PlanCandidateDiscardedResponse": "PLAN_CANDIDATE_DISCARDED",
        "ExecutionRunDetailResponse": "EXECUTION_RUN_DETAIL",
    }
    for schema_name, contract_type in top_level_responses.items():
        response_schema = openapi["components"]["schemas"][schema_name]
        if "contractType" not in response_schema["required"]:
            raise AssertionError(f"{schema_name} does not require contractType")
        if response_schema["properties"]["contractType"]["const"] != contract_type:
            raise AssertionError(f"{schema_name} contractType differs")

    same_start = json.loads(
        (APS_ROOT / "examples/golden/valid-same-start-input.json").read_text(encoding="utf-8")
    )
    same_validation = json.loads(
        (APS_ROOT / "examples/golden/valid-same-start-validation-result.json").read_text(encoding="utf-8")
    )
    if same_start["dependencies"][0]["relationType"] != "SAME_START":
        raise AssertionError("SAME_START input example lost source semantics")
    if same_validation["validationStatus"] != "FAIL" or same_validation["publishable"]:
        raise AssertionError("SAME_START validation must block solving and publishing")
    if not any(problem["reasonCode"] == "UNSUPPORTED_SYNC_RULE" for problem in same_validation["problems"]):
        raise AssertionError("SAME_START validation lost UNSUPPORTED_SYNC_RULE")
    if same_start["inputHash"] != same_validation["inputHash"]:
        raise AssertionError("SAME_START input and validation hashes differ")
    print("OpenAPI relative refs and cross-contract invariants: PASS")


def verify_jcs(manifest: dict[str, Any]) -> None:
    node_script = APS_ROOT / "tools" / "verify-jcs.mjs"
    completed = subprocess.run(
        ["node", str(node_script), str(MANIFEST_PATH)],
        cwd=APS_ROOT.parents[1],
        check=False,
        text=True,
        encoding="utf-8",
    )
    if completed.returncode != 0:
        raise AssertionError(f"Node JCS verifier failed with exit code {completed.returncode}")


def main() -> int:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    verify_toolchain(manifest)
    schemas = verify_schema_cases(manifest)
    verify_openapi_and_cross_contracts(manifest, schemas)
    verify_jcs(manifest)
    print("APS unified contract verification: PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"APS unified contract verification: FAIL: {error}", file=sys.stderr)
        raise
