#!/usr/bin/env python3
"""Compile/run minimal Java, TypeScript and Pydantic compatibility probes."""

from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Annotated, Literal


ADEN_ROOT = Path(__file__).resolve().parents[1]
WORKSPACE_ROOT = ADEN_ROOT.parents[1]
GENERATED_BANNER = "GENERATED FROM contracts/aden — DO NOT EDIT"
VALID = ["0", "1", "9007199254740991", "9007199254740992", "9223372036854775807"]
INVALID = ["", "00", "01", "+1", "-1", "1.0", "1e3", "9223372036854775808", "9999999999999999999"]
COMMANDS = ["SUBMIT_FOR_VALIDATION", "REQUEST_CANCEL"]


def run(command: list[str], cwd: Path) -> str:
    completed = subprocess.run(command, cwd=cwd, text=True, capture_output=True, timeout=60)
    if completed.returncode != 0:
        raise RuntimeError(
            f"command failed ({completed.returncode}): {' '.join(command)}\n"
            f"stdout:\n{completed.stdout}\nstderr:\n{completed.stderr}"
        )
    return (completed.stdout + completed.stderr).strip()


def load_contract_values() -> tuple[str, list[str]]:
    common = json.loads((ADEN_ROOT / "schemas/current/common.schema.json").read_text(encoding="utf-8"))
    dictionary = json.loads((ADEN_ROOT / "dictionaries/current-v1.json").read_text(encoding="utf-8"))
    pattern = common["$defs"]["CanonicalInt64String"]["pattern"]
    commands = list(dictionary["publicOperatorCommands"])
    if commands != COMMANDS:
        raise RuntimeError(f"public command order/value changed: {commands}")
    return pattern, commands


def probe_python(pattern: str) -> str:
    try:
        import pydantic
        from pydantic import StringConstraints, TypeAdapter, ValidationError
    except ImportError as exc:
        raise RuntimeError(f"Pydantic v2 is required: {exc}") from exc
    if int(pydantic.__version__.split(".", 1)[0]) != 2:
        raise RuntimeError(f"Pydantic v2 required, found {pydantic.__version__}")
    Scalar = Annotated[str, StringConstraints(pattern=pattern)]
    Command = Literal["SUBMIT_FOR_VALIDATION", "REQUEST_CANCEL"]
    scalar_adapter = TypeAdapter(Scalar)
    command_adapter = TypeAdapter(Command)
    for value in VALID:
        if scalar_adapter.validate_python(value) != value:
            raise RuntimeError(f"Pydantic changed scalar {value}")
    for value in INVALID:
        try:
            scalar_adapter.validate_python(value)
        except ValidationError:
            pass
        else:
            raise RuntimeError(f"Pydantic accepted forbidden scalar {value!r}")
    for command in COMMANDS:
        command_adapter.validate_python(command)
    try:
        command_adapter.validate_python("COMPLETE")
    except ValidationError:
        pass
    else:
        raise RuntimeError("Pydantic accepted internal command COMPLETE")
    return f"Python {sys.version_info.major}.{sys.version_info.minor}; Pydantic {pydantic.__version__}"


def probe_typescript(temp: Path, pattern: str) -> str:
    tsc = WORKSPACE_ROOT / "frontend/desktop/aden-desktop/node_modules/.bin/tsc.cmd"
    if not tsc.is_file():
        discovered = shutil.which("tsc")
        if not discovered:
            raise RuntimeError("TypeScript compiler not found")
        tsc = Path(discovered)
    source = temp / "contract-probe.ts"
    source.write_text(
        f"// {GENERATED_BANNER}\n"
        "type Int64DecimalString = string & { readonly __brand: unique symbol };\n"
        "type OperatorCommand = 'SUBMIT_FOR_VALIDATION' | 'REQUEST_CANCEL';\n"
        f"const pattern = new RegExp({json.dumps(pattern)});\n"
        f"const valid: string[] = {json.dumps(VALID)};\n"
        f"const invalid: string[] = {json.dumps(INVALID)};\n"
        f"const commands: OperatorCommand[] = {json.dumps(COMMANDS)};\n"
        "function parse(value: unknown): Int64DecimalString {\n"
        "  if (typeof value !== 'string' || !pattern.test(value)) throw new Error('invalid int64');\n"
        "  return value as Int64DecimalString;\n"
        "}\n"
        "for (const value of valid) if (parse(value) !== value) throw new Error('round-trip');\n"
        "for (const value of invalid) { let rejected = false; try { parse(value); } catch { rejected = true; } if (!rejected) throw new Error('accepted '+value); }\n"
        "if (commands.join(',') !== 'SUBMIT_FOR_VALIDATION,REQUEST_CANCEL') throw new Error('commands');\n"
        "console.log('typescript-probe-pass');\n",
        encoding="utf-8",
    )
    out_dir = temp / "ts-out"
    run([str(tsc), "--strict", "--target", "ES2022", "--module", "commonjs", "--outDir", str(out_dir), str(source)], temp)
    node = shutil.which("node")
    if not node:
        raise RuntimeError("Node.js not found")
    output = run([node, str(out_dir / "contract-probe.js")], temp)
    if "typescript-probe-pass" not in output:
        raise RuntimeError(f"unexpected TypeScript probe output: {output}")
    version = run([str(tsc), "--version"], temp)
    return f"Node {run([node, '--version'], temp)}; {version}"


def probe_java(temp: Path, pattern: str) -> str:
    javac = shutil.which("javac")
    java = shutil.which("java")
    if not javac or not java:
        raise RuntimeError("JDK javac/java not found")
    escaped_pattern = pattern.replace("\\", "\\\\").replace('"', '\\"')
    quoted_valid = ", ".join(json.dumps(item) for item in VALID)
    quoted_invalid = ", ".join(json.dumps(item) for item in INVALID)
    source = temp / "AdenContractProbe.java"
    source.write_text(
        f"// {GENERATED_BANNER}\n"
        "import java.util.Set;\n"
        "import java.util.regex.Pattern;\n"
        "public final class AdenContractProbe {\n"
        f"  private static final Pattern INT64 = Pattern.compile(\"{escaped_pattern}\");\n"
        f"  private static final String[] VALID = {{{quoted_valid}}};\n"
        f"  private static final String[] INVALID = {{{quoted_invalid}}};\n"
        "  private static final Set<String> COMMANDS = Set.of(\"SUBMIT_FOR_VALIDATION\", \"REQUEST_CANCEL\");\n"
        "  public static void main(String[] args) {\n"
        "    for (String value : VALID) { if (!INT64.matcher(value).matches()) throw new AssertionError(value); Long.parseLong(value); }\n"
        "    for (String value : INVALID) if (INT64.matcher(value).matches()) throw new AssertionError(value);\n"
        "    if (COMMANDS.contains(\"COMPLETE\") || COMMANDS.size() != 2) throw new AssertionError(\"commands\");\n"
        "    System.out.println(\"java-probe-pass\");\n"
        "  }\n"
        "}\n",
        encoding="utf-8",
    )
    run([javac, "--release", "17", str(source)], temp)
    output = run([java, "-cp", str(temp), "AdenContractProbe"], temp)
    if "java-probe-pass" not in output:
        raise RuntimeError(f"unexpected Java probe output: {output}")
    return run([javac, "-version"], temp)


def main() -> int:
    pattern, _ = load_contract_values()
    if any(bool(re.fullmatch(pattern, item)) is False for item in VALID):
        raise RuntimeError("Python regex preflight rejected a valid boundary")
    if any(bool(re.fullmatch(pattern, item)) is True for item in INVALID):
        raise RuntimeError("Python regex preflight accepted an invalid boundary")
    with tempfile.TemporaryDirectory(prefix="aden-consumer-probe-") as raw:
        temp = Path(raw)
        results = [probe_python(pattern), probe_typescript(temp, pattern), probe_java(temp, pattern)]
    print("PASS: Aden consumer compatibility probe — " + "; ".join(results))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, subprocess.TimeoutExpired) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
