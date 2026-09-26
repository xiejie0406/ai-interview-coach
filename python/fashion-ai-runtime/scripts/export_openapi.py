"""从运行时模型确定性导出唯一提交版 OpenAPI 契约。"""

from pathlib import Path

import yaml

from fashion_ai.main import create_app

CONTRACT_PATH = (
    Path(__file__).resolve().parents[3]
    / "contracts"
    / "fashion"
    / "ai-runtime.openapi.yaml"
)


def main() -> None:
    contract = create_app().openapi()
    CONTRACT_PATH.write_text(
        yaml.safe_dump(
            contract,
            allow_unicode=True,
            sort_keys=False,
            width=100,
        ),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
