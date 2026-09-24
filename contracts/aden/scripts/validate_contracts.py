#!/usr/bin/env python3
"""Validate and locally bundle the FEAT-ADEN-001 contract package.

The gate intentionally rejects remote and absolute refs. It treats JSON Schema as
the only body/event definition and validates each manifest example twice: once
against its direct schema and once through the bound OpenAPI operation/component.
"""

from __future__ import annotations

import argparse
import copy
import json
import re
import sys
import tempfile
from pathlib import Path
from typing import Any, Iterable
from urllib.parse import unquote, urldefrag, urljoin, urlparse

try:
    from jsonschema import Draft202012Validator, FormatChecker
    from referencing import Registry, Resource
    from referencing.jsonschema import DRAFT202012
except ImportError as exc:  # pragma: no cover - explicit environment failure
    raise SystemExit(f"ERROR: jsonschema>=4 with referencing is required: {exc}")


ADEN_ROOT = Path(__file__).resolve().parents[1]
SCHEMA_ROOT = ADEN_ROOT / "schemas"
OPENAPI_ROOT = ADEN_ROOT / "openapi"
EXAMPLE_ROOT = ADEN_ROOT / "examples"
WORKSPACE_ROOT = ADEN_ROOT.parents[1]


class GateError(RuntimeError):
    pass


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise GateError(f"cannot parse JSON {path.relative_to(WORKSPACE_ROOT)}: {exc}") from exc


def pointer_get(document: Any, fragment: str) -> Any:
    if not fragment:
        return document
    if not fragment.startswith("/"):
        raise GateError(f"only JSON Pointer fragments are allowed, got #{fragment}")
    current = document
    for raw in fragment[1:].split("/"):
        token = unquote(raw).replace("~1", "/").replace("~0", "~")
        try:
            current = current[int(token)] if isinstance(current, list) else current[token]
        except (KeyError, IndexError, ValueError, TypeError) as exc:
            raise GateError(f"unresolvable JSON Pointer #{fragment}") from exc
    return current


def path_from_file_uri(uri: str) -> Path:
    parsed = urlparse(uri)
    if parsed.scheme != "file":
        raise GateError(f"remote/non-file $ref is forbidden: {uri}")
    raw_path = unquote(parsed.path)
    if parsed.netloc:
        raw_path = f"//{parsed.netloc}{raw_path}"
    if re.match(r"^/[A-Za-z]:/", raw_path):
        raw_path = raw_path[1:]
    return Path(raw_path).resolve()


def ensure_local_ref(base_path: Path, ref: str) -> tuple[Path, str]:
    parsed_ref = urlparse(ref)
    if parsed_ref.scheme or parsed_ref.netloc or Path(parsed_ref.path).is_absolute():
        raise GateError(f"absolute or remote $ref is forbidden in {base_path.name}: {ref}")
    absolute, fragment = urldefrag(urljoin(base_path.resolve().as_uri(), ref))
    target_path = path_from_file_uri(absolute)
    try:
        target_path.relative_to(ADEN_ROOT)
    except ValueError as exc:
        raise GateError(f"$ref escapes contracts/aden: {ref} from {base_path}") from exc
    if target_path.suffix.lower() != ".json" or not target_path.is_file():
        raise GateError(f"$ref target does not exist or is not JSON: {ref} from {base_path.name}")
    return target_path, fragment


def walk_refs(node: Any, source_path: Path, seen: set[tuple[Path, str]]) -> int:
    count = 0
    if isinstance(node, dict):
        if "$ref" in node:
            ref = node["$ref"]
            if not isinstance(ref, str):
                raise GateError(f"non-string $ref in {source_path.name}")
            target_path, fragment = ensure_local_ref(source_path, ref)
            key = (target_path, fragment)
            count += 1
            if key not in seen:
                seen.add(key)
                target = pointer_get(load_json(target_path), fragment)
                count += walk_refs(target, target_path, seen)
        for key, value in node.items():
            if key != "$ref":
                count += walk_refs(value, source_path, seen)
    elif isinstance(node, list):
        for value in node:
            count += walk_refs(value, source_path, seen)
    return count


def bundle(node: Any, source_path: Path, stack: tuple[tuple[Path, str], ...] = ()) -> Any:
    if isinstance(node, dict):
        if "$ref" in node:
            if set(node) != {"$ref"}:
                raise GateError(f"$ref siblings are forbidden for deterministic bundling in {source_path.name}: {node}")
            target_path, fragment = ensure_local_ref(source_path, node["$ref"])
            key = (target_path, fragment)
            if key in stack:
                raise GateError(f"cyclic $ref cannot be fully bundled: {target_path.name}#{fragment}")
            target = pointer_get(load_json(target_path), fragment)
            return bundle(copy.deepcopy(target), target_path, stack + (key,))
        return {key: bundle(value, source_path, stack) for key, value in node.items()}
    if isinstance(node, list):
        return [bundle(value, source_path, stack) for value in node]
    return node


def make_registry(paths: Iterable[Path]) -> Registry:
    registry = Registry()
    for path in paths:
        registry = registry.with_resource(
            path.resolve().as_uri(),
            Resource.from_contents(load_json(path), default_specification=DRAFT202012),
        )
    return registry


def validator_for_ref(source_path: Path, ref: str, registry: Registry) -> Draft202012Validator:
    target_path, fragment = ensure_local_ref(source_path, ref)
    absolute_ref = target_path.resolve().as_uri() + (f"#{fragment}" if fragment else "")
    return Draft202012Validator(
        {"$ref": absolute_ref},
        registry=registry,
        format_checker=FormatChecker(),
    )


def find_operation(document: dict[str, Any], operation_id: str) -> dict[str, Any]:
    matches = []
    for path_item in document.get("paths", {}).values():
        if not isinstance(path_item, dict):
            continue
        for method in ("get", "post", "put", "patch", "delete", "options", "head"):
            operation = path_item.get(method)
            if isinstance(operation, dict) and operation.get("operationId") == operation_id:
                matches.append(operation)
    if len(matches) != 1:
        raise GateError(f"operationId {operation_id!r} resolved {len(matches)} times")
    return matches[0]


def schema_node_from_openapi(binding: dict[str, Any], manifest_path: Path) -> tuple[Path, dict[str, Any]]:
    document_path = (manifest_path.parent / binding["document"]).resolve()
    document = load_json(document_path)
    location = binding["location"]
    if location == "componentSchema":
        try:
            return document_path, document["components"]["schemas"][binding["component"]]
        except KeyError as exc:
            raise GateError(f"missing OpenAPI component schema {binding.get('component')}") from exc
    operation = find_operation(document, binding["operationId"])
    if location == "requestBody":
        body = operation.get("requestBody")
    elif location == "responseBody":
        status = str(binding["status"])
        try:
            body = operation["responses"][status]
        except KeyError as exc:
            raise GateError(f"operation {binding['operationId']} lacks response {status}") from exc
        if set(body) == {"$ref"}:
            target_path, fragment = ensure_local_ref(document_path, body["$ref"])
            body = pointer_get(load_json(target_path), fragment)
            document_path = target_path
    else:
        raise GateError(f"unknown OpenAPI example location: {location}")
    if not isinstance(body, dict):
        raise GateError(f"missing {location} on {binding.get('operationId')}")
    media_type = binding.get("mediaType", "application/json")
    try:
        return document_path, body["content"][media_type]["schema"]
    except KeyError as exc:
        raise GateError(f"missing {media_type} schema on {binding.get('operationId')} {location}") from exc


def final_ref(source_path: Path, schema_node: dict[str, Any]) -> tuple[Path, str] | None:
    current_path = source_path
    current = schema_node
    seen: set[tuple[Path, str]] = set()
    while isinstance(current, dict) and set(current) == {"$ref"}:
        target_path, fragment = ensure_local_ref(current_path, current["$ref"])
        key = (target_path, fragment)
        if key in seen:
            raise GateError(f"cyclic schema ref at {target_path.name}#{fragment}")
        seen.add(key)
        current = pointer_get(load_json(target_path), fragment)
        current_path = target_path
    return (current_path, "") if not seen else key


def validate_openapi_structure(path: Path, document: dict[str, Any]) -> int:
    if document.get("openapi") != "3.1.0":
        raise GateError(f"{path.name}: openapi must be 3.1.0")
    if document.get("jsonSchemaDialect") != "https://json-schema.org/draft/2020-12/schema":
        raise GateError(f"{path.name}: JSON Schema dialect must be 2020-12")
    paths = document.get("paths")
    if not isinstance(paths, dict) or not paths:
        raise GateError(f"{path.name}: no paths")
    if any("/agent/" in item or "agent-workers" in item for item in paths):
        raise GateError(f"{path.name}: Agent Worker path leaked into current OpenAPI")
    security_schemes = document.get("components", {}).get("securitySchemes", {})
    if any("Agent" in name for name in security_schemes):
        raise GateError(f"{path.name}: Agent security leaked into current OpenAPI")

    operation_ids: set[str] = set()
    operations = 0
    for route, path_item in paths.items():
        if not route.startswith("/api/v1/aden/"):
            raise GateError(f"{path.name}: unversioned/non-Aden route {route}")
        for method in ("get", "post", "put", "patch", "delete", "options", "head"):
            operation = path_item.get(method)
            if operation is None:
                continue
            operations += 1
            operation_id = operation.get("operationId")
            if not isinstance(operation_id, str) or operation_id in operation_ids:
                raise GateError(f"{path.name}: missing/duplicate operationId {operation_id!r}")
            operation_ids.add(operation_id)
            if not operation.get("responses"):
                raise GateError(f"{path.name}: operation {operation_id} has no responses")

    schemas = document.get("components", {}).get("schemas", {})
    if not schemas:
        raise GateError(f"{path.name}: no component schema refs")
    for name, schema in schemas.items():
        if not isinstance(schema, dict) or set(schema) != {"$ref"}:
            raise GateError(f"{path.name}: components.schemas.{name} duplicates a hand-written DTO")
        ref = schema["$ref"]
        if ref.startswith("#") or "../schemas/current/" not in ref:
            raise GateError(f"{path.name}: components.schemas.{name} must use a relative current Schema ref")
        if "experimental" in ref:
            raise GateError(f"{path.name}: current OpenAPI references experimental Schema")

    def check_content_schema(node: Any, location: str = "$") -> None:
        if isinstance(node, dict):
            if "content" in node and isinstance(node["content"], dict):
                for media_type, media in node["content"].items():
                    if isinstance(media, dict) and "schema" in media:
                        schema = media["schema"]
                        if not isinstance(schema, dict) or set(schema) != {"$ref"} or not schema["$ref"].startswith("#/components/schemas/"):
                            raise GateError(f"{path.name}: body schema at {location}/content/{media_type} is hand-written")
            for key, value in node.items():
                check_content_schema(value, f"{location}/{key}")
        elif isinstance(node, list):
            for index, value in enumerate(node):
                check_content_schema(value, f"{location}/{index}")

    check_content_schema(document)
    return operations


def validate_dictionary(dictionary: dict[str, Any], operator_doc: dict[str, Any]) -> None:
    public = dictionary.get("publicOperatorCommands")
    expected = {"SUBMIT_FOR_VALIDATION": "aden:task:command", "REQUEST_CANCEL": "aden:task:cancel"}
    if public != expected:
        raise GateError(f"public Operator command/permission mapping changed: {public}")
    operation = find_operation(operator_doc, "commandTask")
    if operation.get("x-aden-command-permissions") != expected:
        raise GateError("Operator OpenAPI command permissions diverge from dictionary")
    internal = set(dictionary.get("internalCommands", []))
    operator_schema_text = (SCHEMA_ROOT / "current" / "operator.schema.json").read_text(encoding="utf-8")
    leaked = sorted(command for command in internal if f'"{command}"' in operator_schema_text)
    if leaked:
        raise GateError(f"internal commands leaked into Operator DTO Schema: {leaked}")
    error_rows = dictionary.get("errors", [])
    pairs = {(row["http"], row["errorCode"]) for row in error_rows}
    if len(pairs) != len(error_rows):
        raise GateError("duplicate dictionary error status/code pair")


def validate_generation_policy() -> None:
    policy = load_json(ADEN_ROOT / "generation-policy.json")
    readme = (ADEN_ROOT / "README.md").read_text(encoding="utf-8")
    banner = policy.get("immutableBanner")
    if not banner or banner not in readme or "禁止手工修改生成物" not in readme:
        raise GateError("README lacks the machine-declared generated-file immutability notice")
    ids: set[str] = set()
    outputs: set[str] = set()
    for consumer in policy.get("consumers", []):
        consumer_id = consumer.get("id")
        output = consumer.get("outputDirectory")
        if not consumer_id or consumer_id in ids:
            raise GateError("generation-policy has missing/duplicate consumer id")
        ids.add(consumer_id)
        if consumer.get("mutable") is not False:
            raise GateError(f"generated consumer {consumer_id} is not marked immutable")
        if not consumer.get("workingDirectory") or not consumer.get("command"):
            raise GateError(f"consumer {consumer_id} lacks an executable validation command")
        generates = consumer.get("generatedArtifacts") is True
        if not generates and output is not None:
            raise GateError(f"consumer {consumer_id} claims no generated artifacts but has an output")
        if generates:
            if not isinstance(output, str) or not output or output in outputs:
                raise GateError(f"generated consumer {consumer_id} lacks a unique output")
            outputs.add(output)
            normalized = output.replace("\\", "/")
            if normalized.startswith("contracts/aden/schemas") or normalized.startswith("contracts/aden/openapi"):
                raise GateError(f"generated output overlaps canonical sources: {output}")


def validate_configuration(configuration: dict[str, Any]) -> int:
    if configuration.get("schemaVersion") != 1 or configuration.get("bindingPrefix") != "aden":
        raise GateError("configuration/current-v1.json has an unsupported version or prefix")
    settings = configuration.get("settings")
    if not isinstance(settings, list) or not settings:
        raise GateError("configuration/current-v1.json has no settings")

    required_keys = {
        "aden.runner.session.ttl-seconds",
        "aden.runner.session.heartbeat-interval-seconds",
        "aden.runner.delivery.lease-ttl-seconds",
        "aden.runner.claim.max-batch-size",
        "aden.runner.session.max-capacity",
        "aden.stream.heartbeat-interval-seconds",
        "aden.stream.connection-ttl-seconds",
        "aden.stream.replay-batch-size",
        "aden.stream.per-connection-queue-capacity",
        "aden.stream.send-timeout-seconds",
        "aden.outbox.claim-ttl-seconds",
        "aden.outbox.retry.initial-delay-milliseconds",
        "aden.outbox.retry.max-delay-milliseconds",
        "aden.outbox.retry.max-attempts",
        "aden.idempotency.in-progress-ttl-seconds",
        "aden.idempotency.retention-seconds",
        "aden.event.retention-seconds",
    }
    ids: set[str] = set()
    keys: set[str] = set()
    by_id: dict[str, dict[str, Any]] = {}
    for setting in settings:
        if not isinstance(setting, dict):
            raise GateError("configuration setting must be an object")
        setting_id = setting.get("id")
        key = setting.get("externalKey")
        if not isinstance(setting_id, str) or not re.fullmatch(r"[a-z][A-Za-z0-9]+", setting_id):
            raise GateError(f"invalid configuration id: {setting_id!r}")
        if setting_id in ids or not isinstance(key, str) or key in keys:
            raise GateError(f"missing/duplicate configuration id or key: {setting_id!r}, {key!r}")
        if not re.fullmatch(r"aden\.(?:runner|stream|outbox|idempotency|event)\.[a-z0-9.-]+", key):
            raise GateError(f"invalid external configuration key: {key}")
        ids.add(setting_id)
        keys.add(key)
        by_id[setting_id] = setting
        if setting.get("type") != "integer" or setting.get("unit") not in {"seconds", "milliseconds", "items", "attempts"}:
            raise GateError(f"configuration {key} has unsupported type/unit")
        if setting.get("externallyConfigurable") is not True:
            raise GateError(f"configuration {key} is not externally configurable")
        values = [setting.get(name) for name in ("minimum", "maximum", "productionDefault", "testValue")]
        if any(not isinstance(value, int) or isinstance(value, bool) for value in values):
            raise GateError(f"configuration {key} bounds/defaults must be integers")
        minimum, maximum, production, test = values
        if not minimum <= production <= maximum or not minimum <= test <= maximum:
            raise GateError(f"configuration {key} default/test value is outside [{minimum}, {maximum}]")

    missing = sorted(required_keys - keys)
    if missing:
        raise GateError(f"configuration is missing required external keys: {missing}")

    for profile in ("productionDefault", "testValue"):
        value = lambda setting_id: by_id[setting_id][profile]
        relational_checks = [
            (value("runnerSessionTtl") >= 3 * value("runnerSessionHeartbeatInterval"), "Runner Session TTL/heartbeat"),
            (value("runnerDeliveryLeaseTtl") >= 2 * value("runnerSessionHeartbeatInterval"), "Delivery lease/heartbeat"),
            (value("sseConnectionTtl") >= 3 * value("sseHeartbeatInterval"), "SSE connection TTL/heartbeat"),
            (value("sseSendTimeout") < value("sseConnectionTtl"), "SSE send timeout/connection TTL"),
            (value("outboxRetryMaxDelay") >= value("outboxRetryInitialDelay"), "Outbox retry delay"),
            (value("idempotencyRetention") >= value("runnerDeliveryLeaseTtl"), "idempotency retention/Delivery lease"),
            (value("eventRetention") >= value("sseConnectionTtl"), "event retention/SSE connection TTL"),
        ]
        failed = [name for passed, name in relational_checks if not passed]
        if failed:
            raise GateError(f"configuration {profile} violates invariants: {failed}")
    return len(settings)


def validate_manifest(registry: Registry, dictionary: dict[str, Any]) -> tuple[int, int]:
    manifest_path = EXAMPLE_ROOT / "manifest.json"
    manifest = load_json(manifest_path)
    cases = manifest.get("cases")
    if not isinstance(cases, list) or not cases:
        raise GateError("example manifest has no cases")
    ids: set[str] = set()
    listed_files: set[Path] = set()
    valid_count = 0
    invalid_count = 0
    dictionary_errors = {(row["http"], row["errorCode"], row["retryable"]) for row in dictionary["errors"]}
    for case in cases:
        case_id = case.get("id")
        if not case_id or case_id in ids:
            raise GateError(f"missing/duplicate example id {case_id!r}")
        ids.add(case_id)
        example_path = (manifest_path.parent / case["file"]).resolve()
        try:
            example_path.relative_to(EXAMPLE_ROOT)
        except ValueError as exc:
            raise GateError(f"example escapes examples directory: {case['file']}") from exc
        if not example_path.is_file() or example_path in listed_files:
            raise GateError(f"missing/duplicate example file: {case['file']}")
        listed_files.add(example_path)
        instance = load_json(example_path)
        direct_validator = validator_for_ref(manifest_path, case["schemaRef"], registry)
        direct_errors = list(direct_validator.iter_errors(instance))
        expected_valid = case.get("expectedValid") is True
        if expected_valid == bool(direct_errors):
            detail = direct_errors[0].message if direct_errors else "unexpectedly valid"
            raise GateError(f"{case_id}: direct Schema expectation failed: {detail}")

        binding = case.get("openapi")
        if binding:
            openapi_path, schema_node = schema_node_from_openapi(binding, manifest_path)
            if not isinstance(schema_node, dict) or set(schema_node) != {"$ref"}:
                raise GateError(f"{case_id}: OpenAPI body Schema is not a single $ref")
            openapi_validator = validator_for_ref(openapi_path, schema_node["$ref"], registry)
            openapi_errors = list(openapi_validator.iter_errors(instance))
            if expected_valid == bool(openapi_errors):
                detail = openapi_errors[0].message if openapi_errors else "unexpectedly valid"
                raise GateError(f"{case_id}: OpenAPI Schema expectation failed: {detail}")
            direct_target = final_ref(manifest_path, {"$ref": case["schemaRef"]})
            openapi_target = final_ref(openapi_path, schema_node)
            if direct_target != openapi_target:
                raise GateError(f"{case_id}: direct and OpenAPI Schema refs diverge: {direct_target} != {openapi_target}")

        if "expectedStatus" in case:
            if not isinstance(instance, dict):
                raise GateError(f"{case_id}: status assertion requires object body")
            status = case["expectedStatus"]
            if instance.get("code") is not None and instance["code"] != status:
                raise GateError(f"{case_id}: ErrorEnvelope code does not match HTTP status {status}")
            expected_error = case.get("expectedErrorCode")
            if expected_error:
                if instance.get("errorCode") != expected_error:
                    raise GateError(f"{case_id}: errorCode mismatch")
                triple = (status, expected_error, instance.get("retryable"))
                if triple not in dictionary_errors:
                    raise GateError(f"{case_id}: error status/retryability diverges from dictionary")

        valid_count += int(expected_valid)
        invalid_count += int(not expected_valid)

    actual_files = {path.resolve() for path in EXAMPLE_ROOT.rglob("*.json") if path.name != "manifest.json"}
    unlisted = sorted(path.relative_to(EXAMPLE_ROOT).as_posix() for path in actual_files - listed_files)
    if unlisted:
        raise GateError(f"example JSON files missing from manifest: {unlisted}")
    return valid_count, invalid_count


def validate_boundaries(registry: Registry) -> None:
    validator = validator_for_ref(
        SCHEMA_ROOT / "current" / "common.schema.json",
        "#/$defs/CanonicalInt64String",
        registry,
    )
    accepted = ["0", "1", "9007199254740991", "9007199254740992", "9223372036854775807"]
    rejected: list[Any] = [0, 1, 9007199254740992, "", "00", "01", "+1", "-1", "1.0", "1e3", "9223372036854775808", "9999999999999999999"]
    for value in accepted:
        if not validator.is_valid(value):
            raise GateError(f"CanonicalInt64String rejects boundary {value!r}")
    for value in rejected:
        if validator.is_valid(value):
            raise GateError(f"CanonicalInt64String accepts forbidden value {value!r}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bundle-dir", type=Path, help="retain fully dereferenced diagnostic bundles")
    args = parser.parse_args()

    json_paths = sorted(ADEN_ROOT.rglob("*.json"))
    documents = {path: load_json(path) for path in json_paths}
    schema_paths = sorted(SCHEMA_ROOT.rglob("*.schema.json"))
    openapi_paths = sorted(OPENAPI_ROOT.glob("*.openapi.json"))
    if len(schema_paths) != 4 or len(openapi_paths) != 2:
        raise GateError(f"expected 4 Schema and 2 OpenAPI JSON files, got {len(schema_paths)} and {len(openapi_paths)}")

    for path in schema_paths:
        Draft202012Validator.check_schema(documents[path])

    all_resource_paths = schema_paths + openapi_paths
    registry = make_registry(all_resource_paths)
    ref_count = sum(walk_refs(documents[path], path, {(path, "")}) for path in all_resource_paths)
    operations = sum(validate_openapi_structure(path, documents[path]) for path in openapi_paths)

    dictionary = documents[ADEN_ROOT / "dictionaries" / "current-v1.json"]
    configuration = documents[ADEN_ROOT / "configuration" / "current-v1.json"]
    operator_doc = documents[OPENAPI_ROOT / "operator-v1.openapi.json"]
    validate_dictionary(dictionary, operator_doc)
    validate_generation_policy()
    configuration_count = validate_configuration(configuration)
    validate_boundaries(registry)
    valid_count, invalid_count = validate_manifest(registry, dictionary)

    if args.bundle_dir:
        bundle_dir = args.bundle_dir.resolve()
        bundle_dir.mkdir(parents=True, exist_ok=True)
        cleanup = None
    else:
        cleanup = tempfile.TemporaryDirectory(prefix="aden-contract-bundles-")
        bundle_dir = Path(cleanup.name)
    try:
        bundled = 0
        for path in all_resource_paths:
            output = bundle_dir / f"{path.name}.bundled.json"
            output.write_text(json.dumps(bundle(documents[path], path), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            load_json(output)
            bundled += 1
    finally:
        if cleanup is not None:
            cleanup.cleanup()

    print(
        "PASS: Aden contracts validated — "
        f"{len(schema_paths)} JSON Schemas, {len(openapi_paths)} OpenAPI documents, "
        f"{operations} operations, {ref_count} resolved refs, {bundled} local bundles, "
        f"{valid_count} valid examples, {invalid_count} rejected examples, "
        f"{configuration_count} typed external configuration keys."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except GateError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
