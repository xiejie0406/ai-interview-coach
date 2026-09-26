"""显式 demo 模式的真实内部 API 路径；默认 disabled 由既有测试覆盖。"""

from dataclasses import replace

from fastapi.testclient import TestClient
from support import runtime_settings, signed_get, signed_json_post
from test_api import valid_request as requirement_request
from test_product_attributes import valid_request as product_request
from test_selection_styling import valid_request as selection_request

from fashion_ai.main import create_app
from fashion_ai.settings import ProviderMode


def test_demo_mode_returns_reviewable_results_through_signed_api() -> None:
    settings = replace(runtime_settings(), provider_mode=ProviderMode.DEMO)
    with TestClient(create_app(settings=settings)) as client:
        capabilities = signed_get(client, "/internal/v1/capabilities")
        requirement = signed_json_post(
            client, "/internal/v1/requirement-analysis", requirement_request()
        )
        product = signed_json_post(
            client, "/internal/v1/product-attribute-suggestion", product_request()
        )
        selection = signed_json_post(
            client, "/internal/v1/selection-styling", selection_request()
        )

    assert capabilities.status_code == 200
    assert capabilities.json()["provider"] == {"mode": "demo", "enabled": True}
    assert requirement.status_code == 200
    assert requirement.json()["result"]["human_confirmation_required"] is True
    assert "本机演示" in requirement.json()["result"]["unresolved_questions"][0]
    assert product.status_code == 200
    assert product.json()["result"]["draft"]["status"] == "pending_human_confirmation"
    assert selection.status_code == 200
    assert len(selection.json()["result"]["tiers"]) == 4
    assert all(
        len(tier["combinations"]) == 1
        for tier in selection.json()["result"]["tiers"]
    )
