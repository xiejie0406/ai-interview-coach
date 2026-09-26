"""三个产品 Agent Family 与当前合成 harness 的版本化注册表。"""

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class AgentFamilyDefinition:
    name: str
    run_modes: tuple[str, ...]
    run_spec_shape_version: int
    executable_in_current_feature: bool = False


class AgentRegistry:
    _PRODUCT_FAMILIES = (
        AgentFamilyDefinition(
            "CustomerServiceAgent",
            ("classify_intent", "draft_reply", "explain_escalation"),
            1,
        ),
        AgentFamilyDefinition(
            "SourcingAgent",
            (
                "normalize_requirement",
                "plan_discovery",
                "draft_supplier_message",
                "extract_quote",
                "explain_comparison",
            ),
            1,
        ),
        AgentFamilyDefinition(
            "CatalogUnderstandingAgent",
            ("map_product_fields", "resolve_sku_terms", "explain_uncertainty"),
            1,
        ),
    )
    _SYNTHETIC = AgentFamilyDefinition("SYNTHETIC_CORE", ("fixture",), 1, True)

    def product_families(self) -> tuple[AgentFamilyDefinition, ...]:
        return self._PRODUCT_FAMILIES

    def require_current_executable(self, family: str) -> AgentFamilyDefinition:
        if family != self._SYNTHETIC.name:
            raise ValueError("AGENT_FAMILY_NOT_ENABLED")
        return self._SYNTHETIC
