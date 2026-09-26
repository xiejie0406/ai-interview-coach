"""只读取本地文件的 JSON Schema 2020-12 校验器。"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections.abc import Iterable
from pathlib import Path
from typing import Protocol, cast

from jsonschema import Draft202012Validator, FormatChecker
from jsonschema.exceptions import SchemaError, ValidationError
from referencing import Registry, Resource
from referencing.exceptions import Unresolvable
from referencing.jsonschema import DRAFT202012, Schema, SchemaRegistry

type JsonScalar = str | int | float | bool | None
type JsonValue = JsonScalar | list[JsonValue] | dict[str, JsonValue]
type JsonObject = dict[str, JsonValue]

_URI_SCHEME = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*:")


class _InstanceValidator(Protocol):
    def iter_errors(self, instance: JsonValue) -> Iterable[ValidationError]: ...


class ContractValidationError(ValueError):
    """Schema 或实例不满足本地契约。"""


def _reject_remote_refs(value: JsonValue) -> None:
    if isinstance(value, dict):
        ref = value.get("$ref")
        if isinstance(ref, str) and (
            _URI_SCHEME.match(ref) or ref.startswith(("//", "/", "\\"))
        ):
            raise ContractValidationError(f"禁止非本地相对 $ref：{ref}")
        for nested in value.values():
            _reject_remote_refs(nested)
    elif isinstance(value, list):
        for nested in value:
            _reject_remote_refs(nested)


def _load_json_object(path: Path) -> JsonObject:
    try:
        raw = cast(object, json.loads(path.read_text(encoding="utf-8")))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ContractValidationError(f"无法读取 JSON：{path}: {exc}") from exc
    if not isinstance(raw, dict):
        raise ContractValidationError(f"JSON 顶层必须是 object：{path}")
    return cast(JsonObject, raw)


def _allowed_schema_root(schema_path: Path) -> Path:
    resolved = schema_path.resolve()
    return next(
        (parent for parent in resolved.parents if parent.name == "schemas"),
        resolved.parent,
    )


def _local_registry(schema_path: Path) -> SchemaRegistry:
    root = _allowed_schema_root(schema_path)
    registry: SchemaRegistry = Registry()
    for candidate in root.rglob("*.json"):
        document = _load_json_object(candidate)
        _reject_remote_refs(document)
        resource: Resource[Schema] = Resource.from_contents(
            cast(Schema, document),
            default_specification=DRAFT202012,
        )
        registry = registry.with_resource(candidate.resolve().as_uri(), resource)
    return registry


def validate_schema_file(schema_path: Path) -> None:
    """校验一个本地 JSON Schema 文档自身的合法性。"""

    schema = _load_json_object(schema_path)
    _reject_remote_refs(schema)
    try:
        Draft202012Validator.check_schema(schema)
    except SchemaError as exc:
        raise ContractValidationError(f"Schema 非法：{schema_path}: {exc.message}") from exc


def validate_instance_file(
    schema_path: Path,
    instance_path: Path,
    fragment: str | None = None,
) -> None:
    """以本地 Schema 校验一个本地 JSON 实例。"""

    instance = _load_json_object(instance_path)
    validate_schema_file(schema_path)
    if fragment is not None and fragment != "" and not fragment.startswith("/"):
        raise ContractValidationError(f"Schema fragment 必须是 JSON Pointer：#{fragment}")
    schema_ref = schema_path.resolve().as_uri()
    if fragment:
        schema_ref = f"{schema_ref}#{fragment}"
    validation_schema: Schema = {"$ref": schema_ref}
    validator = cast(
        _InstanceValidator,
        Draft202012Validator(
            validation_schema,
            format_checker=FormatChecker(),
            registry=_local_registry(schema_path),
        ),
    )
    try:
        errors = sorted(validator.iter_errors(instance), key=lambda item: list(item.absolute_path))
    except Unresolvable as exc:
        raise ContractValidationError(f"存在未解析的本地 $ref：{exc.ref}") from exc
    if errors:
        first = errors[0]
        location = "/".join(str(part) for part in first.absolute_path) or "<root>"
        raise ContractValidationError(
            f"实例不符合 Schema：{instance_path} @ {location}: {first.message}"
        )


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="校验 Aden 本地 JSON 契约")
    commands = parser.add_subparsers(dest="command", required=True)
    schema = commands.add_parser("schema", help="校验 JSON Schema 文档")
    schema.add_argument("schema_path", type=Path)
    instance = commands.add_parser("instance", help="校验 JSON 实例")
    instance.add_argument("schema_ref", help="Schema 路径，可带 #/ JSON Pointer")
    instance.add_argument("instance_path", type=Path)
    return parser


def _split_schema_ref(value: str) -> tuple[Path, str | None]:
    path_text, separator, fragment = value.partition("#")
    if not path_text:
        raise ContractValidationError("Schema 路径不能为空")
    return Path(path_text), fragment if separator else None


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "schema":
            validate_schema_file(args.schema_path)
        else:
            schema_path, fragment = _split_schema_ref(args.schema_ref)
            validate_instance_file(schema_path, args.instance_path, fragment)
    except ContractValidationError as exc:
        print(str(exc), file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
