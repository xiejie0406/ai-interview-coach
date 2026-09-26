import json
from pathlib import Path
from typing import get_args

import yaml

from fashion_ai.domain.models import ErrorCode
from fashion_ai.main import create_app

CONTRACT_PATH = (
    Path(__file__).resolve().parents[3]
    / "contracts"
    / "fashion"
    / "ai-runtime.openapi.yaml"
)
FASHION_CONTRACT_ROOT = CONTRACT_PATH.parent
GATEWAY_CONTRACT_PATH = FASHION_CONTRACT_ROOT / "ai-control-gateway.openapi.yaml"
COMMON_SCHEMA_PATH = (
    FASHION_CONTRACT_ROOT / "schemas" / "v1" / "internal-common.schema.json"
)
AUTH_VECTORS_PATH = (
    FASHION_CONTRACT_ROOT / "examples" / "v1" / "service-authentication-vectors.json"
)


def test_committed_contract_exactly_matches_runtime_openapi() -> None:
    committed = yaml.safe_load(CONTRACT_PATH.read_text(encoding="utf-8"))
    generated = create_app().openapi()
    assert committed == generated


def test_runtime_keeps_the_internal_paths_methods_and_version_shape() -> None:
    generated = create_app().openapi()

    assert {path: set(path_item) for path, path_item in generated["paths"].items()} == {
        "/health": {"get"},
        "/internal/v1/capabilities": {"get"},
        "/internal/v1/requirement-analysis": {"post"},
        "/internal/v1/product-attribute-suggestion": {"post"},
        "/internal/v1/selection-styling": {"post"},
        "/internal/v1/image-generation/submit": {"post"},
    }
    schemas = generated["components"]["schemas"]
    assert schemas["ErrorEnvelope"]["properties"]["version"]["const"] == "1.0"
    assert set(schemas["ErrorEnvelope"]["required"]) == {
        "version",
        "request_id",
        "correlation_id",
        "run_id",
        "trace_id",
        "error",
    }


def test_runtime_internal_operations_declare_full_hmac_and_context_headers() -> None:
    generated = create_app().openapi()
    expected_parameter_names = {
        "X-Fashion-Service-Id",
        "X-Fashion-Key-Id",
        "X-Fashion-Timestamp",
        "X-Fashion-Nonce",
        "X-Fashion-Audience",
        "X-Fashion-Content-SHA256",
        "X-Request-Id",
        "X-Correlation-Id",
        "traceparent",
        "tracestate",
    }

    for path, method in (
        ("/internal/v1/capabilities", "get"),
        ("/internal/v1/requirement-analysis", "post"),
        ("/internal/v1/product-attribute-suggestion", "post"),
        ("/internal/v1/selection-styling", "post"),
        ("/internal/v1/image-generation/submit", "post"),
    ):
        operation = generated["paths"][path][method]
        assert operation["security"] == [{"FashionHmacV1": []}]
        assert {
            parameter["name"] for parameter in operation["parameters"]
        } == expected_parameter_names
        required = {
            parameter["name"]: parameter["required"]
            for parameter in operation["parameters"]
        }
        assert required["tracestate"] is False
        assert all(
            is_required
            for name, is_required in required.items()
            if name != "tracestate"
        )


def test_contract_keeps_budget_and_fact_boundaries_explicit() -> None:
    committed = yaml.safe_load(CONTRACT_PATH.read_text(encoding="utf-8"))
    schemas = committed["components"]["schemas"]

    assert (
        "maximum_minor >= minimum_minor" in schemas["BudgetConstraint"]["description"]
    )
    fact_scope = schemas["RequirementAnalysisResult"]["properties"]["fact_scope"]
    assert fact_scope["type"] == "string"
    assert fact_scope["const"] == "requirements_only"
    assert schemas["RequirementDraft"]["properties"]["category_tiers"]["maxItems"] == 4
    assert (
        schemas["ProductAttributeSuggestionResult"]["properties"]["fact_scope"]["const"]
        == "product_attributes_only"
    )


def test_contract_excludes_customer_fields_and_defines_stable_422() -> None:
    committed = yaml.safe_load(CONTRACT_PATH.read_text(encoding="utf-8"))
    schemas = committed["components"]["schemas"]
    draft_properties = schemas["RequirementDraft"]["properties"]

    assert {
        "customer",
        "customer_ref",
        "customer_id",
        "customer_name",
    }.isdisjoint(draft_properties)
    customer_ref = schemas["RequirementInput"]["properties"]["customer_ref"]
    assert customer_ref["type"] == "string"
    assert customer_ref["minLength"] == 1
    assert customer_ref["maxLength"] == 100
    validation_response = committed["paths"]["/internal/v1/requirement-analysis"][
        "post"
    ]["responses"]["422"]
    assert validation_response["content"]["application/json"]["schema"] == {
        "$ref": "#/components/schemas/ErrorEnvelope"
    }
    assert (
        "REQUEST_VALIDATION_FAILED"
        in schemas["ErrorDetail"]["properties"]["code"]["enum"]
    )


def test_runtime_consumes_gateway_authentication_header_contract() -> None:
    gateway = yaml.safe_load(GATEWAY_CONTRACT_PATH.read_text(encoding="utf-8"))
    parameters = gateway["components"]["parameters"]

    assert parameters["ServiceId"]["schema"]["const"] == "fashion-ai-runtime"
    assert parameters["KeyId"]["schema"] == {
        "type": "string",
        "minLength": 1,
        "maxLength": 64,
        "pattern": "^[A-Za-z0-9][A-Za-z0-9._:-]*$",
    }
    assert parameters["Timestamp"]["schema"]["pattern"] == "^[0-9]{10,12}$"
    assert parameters["Nonce"]["schema"]["minLength"] == 16
    assert parameters["Nonce"]["schema"]["maxLength"] == 128
    assert parameters["RequestId"]["required"] is True
    assert parameters["CorrelationId"]["required"] is True
    assert parameters["Traceparent"]["required"] is True
    assert parameters["Tracestate"]["required"] is False


def test_runtime_error_codes_are_the_shared_v1_subset() -> None:
    shared = json.loads(COMMON_SCHEMA_PATH.read_text(encoding="utf-8"))
    shared_codes = set(shared["$defs"]["errorCode"]["enum"])
    runtime_codes = set(get_args(ErrorCode))
    generated_schemas = create_app().openapi()["components"]["schemas"]

    assert runtime_codes == shared_codes
    assert {
        "SERVICE_AUTHENTICATION_FAILED",
        "SERVICE_NOT_AUTHORIZED",
        "PAYLOAD_TOO_LARGE",
        "REQUEST_VALIDATION_FAILED",
        "RATE_LIMITED",
        "PROVIDER_DISABLED",
    }.issubset(runtime_codes)
    assert set(generated_schemas["ErrorEnvelope"]["required"]) == set(
        shared["$defs"]["errorEnvelope"]["required"]
    )
    assert (
        generated_schemas["ErrorDetail"]["properties"]["message"]["maxLength"]
        == shared["$defs"]["errorDetail"]["properties"]["message"]["maxLength"]
    )


def test_shared_service_authentication_examples_cover_both_directions() -> None:
    document = json.loads(AUTH_VECTORS_PATH.read_text(encoding="utf-8"))
    vectors = document["vectors"]

    assert {vector["name"] for vector in vectors} == {
        "java-agent-runs-execute",
        "python-requirement-analysis",
        "python-to-java-agent-runs-execute",
    }
    assert {(vector["service_id"], vector["audience"]) for vector in vectors} == {
        ("ruoyi-fashion", "fashion-ai-runtime"),
        ("fashion-ai-runtime", "ruoyi-fashion"),
    }
