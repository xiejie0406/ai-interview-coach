"""兼容导入层；新代码应从 :mod:`fashion_ai.settings` 导入。"""

from fashion_ai.settings import (
    API_VERSION,
    DEFAULT_CLOCK_SKEW_SECONDS,
    DEFAULT_MAX_JSON_BODY_BYTES,
    DEFAULT_MAX_NONCE_ENTRIES,
    DEFAULT_NONCE_TTL_SECONDS,
    SERVICE_NAME,
    SERVICE_VERSION,
    HmacKey,
    ProviderMode,
    RuntimeSettings,
    ServiceAuthSettings,
)

__all__ = [
    "API_VERSION",
    "DEFAULT_CLOCK_SKEW_SECONDS",
    "DEFAULT_MAX_JSON_BODY_BYTES",
    "DEFAULT_MAX_NONCE_ENTRIES",
    "DEFAULT_NONCE_TTL_SECONDS",
    "SERVICE_NAME",
    "SERVICE_VERSION",
    "HmacKey",
    "ProviderMode",
    "RuntimeSettings",
    "ServiceAuthSettings",
]
