from datetime import UTC, datetime, timedelta
from uuid import uuid4

from fastapi.testclient import TestClient
from support import runtime_settings, signed_json_post

from fashion_ai.main import create_app

PATH = "/internal/v1/image-generation/submit"


def valid_request() -> dict[str, object]:
    return {
        "version": "1.0",
        "request_id": str(uuid4()),
        "correlation_id": "image-flow-001",
        "run_id": "quote-image-1001",
        "idempotency_key": f"image:{uuid4()}",
        "deadline_at": (datetime.now(UTC) + timedelta(minutes=1)).isoformat(),
        "input": {
            "quote_image_ref": "1001",
            "image_type": "model",
            "requested_count": 2,
            "images": [
                {
                    "slot_code": "SLOT-1",
                    "image_ref": "materials/abc/original.png",
                    "image_hash": "a" * 64,
                }
            ],
            "parameters": {
                "aspect_ratio": "3:4",
                "model_presentation": "企业可用通用模特",
                "scene": "室内纯色背景",
                "pose": "自然站立",
                "prompt_version": "image-v1",
                "background": None,
                "shadow": False,
            },
        },
    }


def test_valid_image_request_is_not_submitted_when_provider_disabled() -> None:
    payload = valid_request()
    with TestClient(create_app(settings=runtime_settings())) as client:
        response = signed_json_post(client, PATH, payload)

    assert response.status_code == 503
    assert response.json()["error"] == {
        "code": "PROVIDER_DISABLED",
        "message": "图片 Provider 未启用，未提交外部任务",
        "retryable": False,
    }


def test_image_contract_rejects_customer_price_inventory_and_notes() -> None:
    for forbidden in ("customer", "price", "inventory", "internal_notes"):
        payload = valid_request()
        payload["input"][forbidden] = "must-not-leave-java"  # type: ignore[index]
        with TestClient(create_app(settings=runtime_settings())) as client:
            response = signed_json_post(client, PATH, payload)
        assert response.status_code == 422
        assert response.json()["error"]["code"] == "REQUEST_VALIDATION_FAILED"
        assert "must-not-leave-java" not in response.text


def test_image_contract_rejects_duplicate_slots_and_more_than_four_results() -> None:
    payload = valid_request()
    payload["input"]["requested_count"] = 5  # type: ignore[index]
    with TestClient(create_app(settings=runtime_settings())) as client:
        response = signed_json_post(client, PATH, payload)
    assert response.status_code == 422

    payload = valid_request()
    images = payload["input"]["images"]  # type: ignore[index]
    images.append(dict(images[0]))  # type: ignore[union-attr,index]
    with TestClient(create_app(settings=runtime_settings())) as client:
        response = signed_json_post(client, PATH, payload)
    assert response.status_code == 422
