import base64

import pytest

from fashion_ai.settings import HmacKey, RuntimeSettings, ServiceAuthSettings


def test_default_settings_have_no_secret_and_fail_closed_configuration() -> None:
    settings = RuntimeSettings.from_env({})

    assert settings.service_auth.configured is False
    assert settings.service_auth.key_ring() == {}
    assert settings.provider_mode.value == "disabled"


def test_active_and_previous_keys_are_loaded_only_from_external_configuration() -> None:
    settings = RuntimeSettings.from_env(
        {
            "FASHION_AI_AUTH_ACTIVE_KEY_ID": "active-2026-09",
            "FASHION_AI_AUTH_ACTIVE_KEY_BASE64": base64.b64encode(
                b"0123456789abcdef0123456789abcdef"
            ).decode(),
            "FASHION_AI_AUTH_PREVIOUS_KEY_ID": "previous-2026-08",
            "FASHION_AI_AUTH_PREVIOUS_KEY_BASE64": base64.b64encode(
                b"abcdef0123456789abcdef0123456789"
            ).decode(),
            "FASHION_AI_AUTH_ALLOWED_SERVICES": "ruoyi-fashion,rotation-probe",
        }
    )

    assert settings.service_auth.configured is True
    assert set(settings.service_auth.key_ring()) == {
        "active-2026-09",
        "previous-2026-08",
    }
    assert settings.service_auth.allowed_service_ids == frozenset(
        {"ruoyi-fashion", "rotation-probe"}
    )
    assert "0123456789abcdef0123456789abcdef" not in repr(settings)
    assert "abcdef0123456789abcdef0123456789" not in repr(settings)


@pytest.mark.parametrize(
    "environment",
    [
        {"FASHION_AI_AUTH_ACTIVE_KEY_ID": "missing-key"},
        {"FASHION_AI_AUTH_ACTIVE_KEY_BASE64": "eA=="},
        {"FASHION_AI_AUTH_PREVIOUS_KEY_ID": "missing-key"},
        {"FASHION_AI_AUTH_PREVIOUS_KEY_BASE64": "eA=="},
    ],
)
def test_partial_key_configuration_is_rejected(
    environment: dict[str, str],
) -> None:
    with pytest.raises(ValueError, match="必须同时配置"):
        RuntimeSettings.from_env(environment)


@pytest.mark.parametrize(
    "encoded",
    ["", "not base64", "%%%", "===="],
)
def test_invalid_or_empty_base64_key_is_rejected(encoded: str) -> None:
    with pytest.raises(ValueError, match=r"Base64|同时配置"):
        RuntimeSettings.from_env(
            {
                "FASHION_AI_AUTH_ACTIVE_KEY_ID": "active-2026-09",
                "FASHION_AI_AUTH_ACTIVE_KEY_BASE64": encoded,
            }
        )


def test_decoded_key_must_have_at_least_32_bytes() -> None:
    with pytest.raises(ValueError, match="至少需要 32 字节"):
        RuntimeSettings.from_env(
            {
                "FASHION_AI_AUTH_ACTIVE_KEY_ID": "active-2026-09",
                "FASHION_AI_AUTH_ACTIVE_KEY_BASE64": base64.b64encode(
                    b"too-short"
                ).decode(),
            }
        )


def test_urlsafe_base64_without_padding_is_accepted() -> None:
    secret = bytes(range(32))
    encoded = base64.urlsafe_b64encode(secret).decode().rstrip("=")
    settings = RuntimeSettings.from_env(
        {
            "FASHION_AI_AUTH_ACTIVE_KEY_ID": "active-2026-09",
            "FASHION_AI_AUTH_ACTIVE_KEY_BASE64": encoded,
        }
    )

    assert settings.service_auth.key_ring()["active-2026-09"] == secret


def test_key_id_accepts_shared_punctuation_up_to_64_characters() -> None:
    key_id = "k._:-" + ("x" * 59)

    key = HmacKey(key_id=key_id, secret=b"x" * 32)

    assert len(key.key_id) == 64


def test_key_id_rejects_more_than_64_characters() -> None:
    with pytest.raises(ValueError, match="1 到 64 位"):
        HmacKey(key_id="k" * 65, secret=b"x" * 32)


def test_service_id_accepts_shared_punctuation_up_to_100_characters() -> None:
    service_id = "s._:-" + ("x" * 95)

    settings = ServiceAuthSettings(allowed_service_ids=frozenset({service_id}))

    assert len(next(iter(settings.allowed_service_ids))) == 100


def test_service_id_rejects_more_than_100_characters() -> None:
    with pytest.raises(ValueError, match="1 到 100 位"):
        ServiceAuthSettings(allowed_service_ids=frozenset({"s" * 101}))


def test_nonce_ttl_cannot_be_shorter_than_replay_window() -> None:
    with pytest.raises(ValueError, match="至少为 600 秒"):
        RuntimeSettings.from_env({"FASHION_AI_AUTH_NONCE_TTL_SECONDS": "599"})


def test_provider_cannot_be_enabled_by_environment() -> None:
    with pytest.raises(ValueError, match="只允许 disabled Provider"):
        RuntimeSettings.from_env({"FASHION_AI_PROVIDER_MODE": "openai"})
