from datetime import UTC, datetime, timedelta
from uuid import uuid4

from fastapi.testclient import TestClient
from support import runtime_settings, signed_json_post

from fashion_ai.main import create_app
from fashion_ai.models import ProductAttributeSuggestionResult


def valid_request() -> dict[str, object]:
    return {
        "version": "1.0",
        "request_id": str(uuid4()),
        "correlation_id": "RUN-950020",
        "run_id": "RUN-950020",
        "idempotency_key": f"product:{uuid4()}",
        "deadline_at": (datetime.now(UTC) + timedelta(minutes=2)).isoformat(),
        "input": {
            "product_ref": "950001",
            "product_row_version": 3,
            "current_attributes": {
                "source_ref": "MANUAL",
                "sku_ref": "000123",
                "name": "黑色员工上衣",
                "category_code": "TOP",
                "color_code": "BLACK",
                "color_name": "黑色",
                "season": "四季",
                "tags": [],
            },
        },
    }


class SuccessfulSuggester:
    async def suggest(self, command: object) -> ProductAttributeSuggestionResult:
        del command
        return ProductAttributeSuggestionResult.model_validate(
            {
                "fact_scope": "product_attributes_only",
                "human_confirmation_required": True,
                "draft": {
                    "status": "pending_human_confirmation",
                    "product_ref": "950001",
                    "category_code": "TOP",
                    "color_code": "BLACK",
                    "color_name": "黑色",
                    "style": "商务休闲",
                    "scene": "员工活动",
                    "audience": "企业员工",
                    "observable_tags": ["纯色", "圆领"],
                },
                "ambiguities": ["季节仍需人工确认"],
            }
        )


def test_product_attribute_suggestion_returns_only_pending_human_draft() -> None:
    payload = valid_request()
    with TestClient(
        create_app(settings=runtime_settings(), product_suggester=SuccessfulSuggester())
    ) as client:
        response = signed_json_post(
            client, "/internal/v1/product-attribute-suggestion", payload
        )

    assert response.status_code == 200
    result = response.json()["result"]
    assert result["fact_scope"] == "product_attributes_only"
    assert result["human_confirmation_required"] is True
    assert result["draft"]["status"] == "pending_human_confirmation"
    assert "price" not in result["draft"]
    assert "stock" not in result["draft"]
    assert "material" not in result["draft"]
    assert "brand" not in result["draft"]


def test_product_attribute_contract_rejects_customer_and_price_data() -> None:
    payload = valid_request()
    attributes = payload["input"]["current_attributes"]  # type: ignore[index]
    attributes["contact_phone"] = "13800138000"  # type: ignore[index]
    attributes["sale_price"] = 99.0  # type: ignore[index]
    with TestClient(create_app(settings=runtime_settings())) as client:
        response = signed_json_post(
            client, "/internal/v1/product-attribute-suggestion", payload
        )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "REQUEST_VALIDATION_FAILED"
    assert "13800138000" not in response.text


def test_product_attribute_suggestion_is_explicitly_disabled_without_provider() -> None:
    payload = valid_request()
    with TestClient(create_app(settings=runtime_settings())) as client:
        response = signed_json_post(
            client, "/internal/v1/product-attribute-suggestion", payload
        )

    assert response.status_code == 503
    assert response.json()["error"] == {
        "code": "PROVIDER_DISABLED",
        "message": "AI Provider 未启用，商品属性建议暂不可用",
        "retryable": False,
    }
