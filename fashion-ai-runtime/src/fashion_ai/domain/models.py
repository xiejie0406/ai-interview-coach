"""内部 API 使用的严格领域值对象与契约模型。"""

import re
from datetime import date, datetime
from itertools import pairwise
from typing import Annotated, Literal
from uuid import UUID

from pydantic import (
    AfterValidator,
    BaseModel,
    ConfigDict,
    Field,
    StrictBool,
    StrictInt,
    StrictStr,
    field_validator,
    model_validator,
)


def _reject_blank(value: str) -> str:
    if not value.strip():
        raise ValueError("不得只包含空白字符")
    return value.strip()


UuidValue = Annotated[UUID, Field(strict=False)]
DeadlineValue = Annotated[datetime, Field(strict=False)]
DateValue = Annotated[date, Field(strict=False)]
ShortText = Annotated[
    StrictStr,
    Field(min_length=1, max_length=120),
    AfterValidator(_reject_blank),
]
LongText = Annotated[
    StrictStr,
    Field(min_length=8, max_length=2_000),
    AfterValidator(_reject_blank),
]
ErrorMessage = Annotated[
    StrictStr,
    Field(min_length=1, max_length=160),
    AfterValidator(_reject_blank),
]
IdempotencyKey = Annotated[
    StrictStr,
    Field(
        min_length=16,
        max_length=128,
        pattern=r"^[A-Za-z0-9][A-Za-z0-9._:-]*$",
    ),
]
CurrencyCode = Literal["CNY"]
StableIdentifier = Annotated[
    StrictStr,
    Field(
        min_length=1,
        max_length=100,
        pattern=r"^[A-Za-z0-9][A-Za-z0-9._:-]*$",
    ),
]
TraceId = Annotated[
    StrictStr,
    Field(pattern=r"^[a-f0-9]{32}$"),
]
PositiveCount = Annotated[StrictInt, Field(ge=1, le=100_000)]
CategoryCount = Annotated[StrictInt, Field(ge=1, le=4)]
CandidateCount = Annotated[StrictInt, Field(ge=1, le=3)]
SelectionMode = Literal["independent", "progressive"]
BudgetBasis = Literal["total", "per_set", "per_item"]
_RFC3339_PREFIX = re.compile(r"^\d{4}-\d{2}-\d{2}T")
_ISO_DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


class StrictModel(BaseModel):
    """拒绝未知字段和隐式类型转换的公共模型基类。"""

    model_config = ConfigDict(extra="forbid", strict=True, frozen=True)


class ProviderCapability(StrictModel):
    mode: Literal["disabled"]
    enabled: Literal[False]


class HealthResponse(StrictModel):
    version: Literal["1.0"]
    service: Literal["fashion-ai-runtime"]
    service_version: Literal["0.1.0"]
    status: Literal["ok"]
    provider: ProviderCapability


class CapabilityOperation(StrictModel):
    name: Literal[
        "requirement-analysis",
        "product-attribute-suggestion",
        "selection-styling",
        "image-generation",
    ]
    versions: tuple[Literal["1.0"]]
    status: Literal["unavailable"]
    reason: Literal["provider_disabled"]


class CapabilitiesResponse(StrictModel):
    version: Literal["1.0"]
    service: Literal["fashion-ai-runtime"]
    provider: ProviderCapability
    operations: tuple[CapabilityOperation, ...]


class BudgetConstraint(StrictModel):
    """以最小货币单位表达预算；要求 maximum_minor >= minimum_minor。"""

    currency: CurrencyCode
    basis: BudgetBasis
    minimum_minor: Annotated[StrictInt, Field(gt=0)]
    maximum_minor: Annotated[StrictInt, Field(gt=0)]
    includes_fees: StrictBool

    @model_validator(mode="after")
    def validate_range(self) -> "BudgetConstraint":
        if self.maximum_minor < self.minimum_minor:
            raise ValueError("maximum_minor 不得小于 minimum_minor")
        return self


class CategorySlot(StrictModel):
    slot_index: CategoryCount
    category: ShortText
    required: StrictBool = True
    notes: ShortText | None = None


def _validate_slots(
    slots: list[CategorySlot], *, expected_count: int
) -> list[CategorySlot]:
    if len(slots) != expected_count:
        raise ValueError("slots 数量必须等于 category_count")
    indexes = [slot.slot_index for slot in slots]
    if indexes != list(range(1, expected_count + 1)):
        raise ValueError("slots.slot_index 必须从 1 开始连续递增")
    categories = [slot.category for slot in slots]
    if len(categories) != len(set(categories)):
        raise ValueError("同一品类档位的 slots.category 不得重复")
    return slots


class CategoryTier(StrictModel):
    category_count: CategoryCount
    slots: Annotated[list[CategorySlot], Field(min_length=1, max_length=4)]
    candidate_count: CandidateCount

    @model_validator(mode="after")
    def validate_slots(self) -> "CategoryTier":
        _validate_slots(self.slots, expected_count=self.category_count)
        return self


def _validate_category_tiers(tiers: list[CategoryTier]) -> list[CategoryTier]:
    counts = [tier.category_count for tier in tiers]
    if counts != sorted(counts) or len(counts) != len(set(counts)):
        raise ValueError("category_tiers.category_count 必须不重复且严格升序")
    return tiers


def _validate_progressive_inheritance(
    selection_mode: SelectionMode | None, tiers: list[CategoryTier]
) -> None:
    if selection_mode != "progressive":
        return
    for previous, current in pairwise(tiers):
        previous_categories = [slot.category for slot in previous.slots]
        current_prefix = [
            slot.category for slot in current.slots[: previous.category_count]
        ]
        if current_prefix != previous_categories:
            raise ValueError("progressive 模式的后续档位必须继承前一档位槽位")


class SizeRequirement(StrictModel):
    category: ShortText | None = None
    size_label: ShortText
    quantity: PositiveCount
    notes: ShortText | None = None


def _validate_size_requirements(
    requirements: list[SizeRequirement],
) -> list[SizeRequirement]:
    keys = [(item.category, item.size_label) for item in requirements]
    if len(keys) != len(set(keys)):
        raise ValueError("size_requirements 中 category 与 size_label 的组合不得重复")
    return requirements


def _validate_budget_constraints(
    constraints: list[BudgetConstraint],
) -> list[BudgetConstraint]:
    bases = [constraint.basis for constraint in constraints]
    if len(bases) != len(set(bases)):
        raise ValueError("budget_constraints.basis 不得重复")
    return constraints


def _empty_size_requirements() -> list[SizeRequirement]:
    return []


def _empty_category_tiers() -> list[CategoryTier]:
    return []


def _empty_budget_constraints() -> list[BudgetConstraint]:
    return []


class KnownRequirements(StrictModel):
    scheme_name: ShortText | None = None
    audience: ShortText | None = None
    scene: ShortText | None = None
    season: ShortText | None = None
    style: ShortText | None = None
    preferred_colors: Annotated[list[ShortText], Field(max_length=20)] = Field(
        default_factory=list
    )
    exclusions: Annotated[list[ShortText], Field(max_length=30)] = Field(
        default_factory=list
    )
    people_count: PositiveCount | None = None
    set_count: PositiveCount | None = None
    delivery_date: DateValue | None = None
    size_requirements: Annotated[list[SizeRequirement], Field(max_length=100)] = Field(
        default_factory=_empty_size_requirements
    )
    selection_mode: SelectionMode | None = None
    category_tiers: Annotated[list[CategoryTier], Field(max_length=4)] = Field(
        default_factory=_empty_category_tiers
    )
    budget_constraints: Annotated[list[BudgetConstraint], Field(max_length=3)] = Field(
        default_factory=_empty_budget_constraints
    )

    @field_validator("delivery_date", mode="before")
    @classmethod
    def delivery_date_must_be_string_or_null(cls, value: object) -> object:
        if value is not None and (
            not isinstance(value, str) or _ISO_DATE.fullmatch(value) is None
        ):
            raise ValueError("delivery_date 必须是 ISO 日期字符串或 null")
        return value

    @field_validator("size_requirements")
    @classmethod
    def size_requirements_must_be_unique(
        cls, value: list[SizeRequirement]
    ) -> list[SizeRequirement]:
        return _validate_size_requirements(value)

    @field_validator("category_tiers")
    @classmethod
    def category_tiers_must_be_ordered(
        cls, value: list[CategoryTier]
    ) -> list[CategoryTier]:
        return _validate_category_tiers(value)

    @field_validator("budget_constraints")
    @classmethod
    def budget_bases_must_be_unique(
        cls, value: list[BudgetConstraint]
    ) -> list[BudgetConstraint]:
        return _validate_budget_constraints(value)

    @model_validator(mode="after")
    def progressive_tiers_must_inherit(self) -> "KnownRequirements":
        _validate_progressive_inheritance(self.selection_mode, self.category_tiers)
        return self


class RequirementInput(StrictModel):
    """客户归属只接收 Java 侧稳定引用，不接收联系人或内部备注。"""

    customer_ref: StableIdentifier
    source_text: LongText
    known_requirements: KnownRequirements = Field(default_factory=KnownRequirements)


class RequirementAnalysisRequest(StrictModel):
    version: Literal["1.0"]
    request_id: UuidValue
    correlation_id: StableIdentifier | None = None
    run_id: StableIdentifier
    idempotency_key: IdempotencyKey
    deadline_at: DeadlineValue
    input: RequirementInput

    @field_validator("deadline_at", mode="before")
    @classmethod
    def deadline_must_be_string(cls, value: object) -> object:
        if not isinstance(value, str) or _RFC3339_PREFIX.match(value) is None:
            raise ValueError("deadline_at 必须是 RFC 3339 字符串")
        return value

    @field_validator("deadline_at")
    @classmethod
    def deadline_must_have_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("deadline_at 必须包含时区")
        return value


class RequirementDraft(StrictModel):
    """仅供人工确认的需求草案，不包含客户归属或商品事实。"""

    status: Literal["pending_human_confirmation"]
    scheme_name: ShortText | None
    audience: ShortText | None
    scene: ShortText | None
    season: ShortText | None
    style: ShortText | None
    preferred_colors: Annotated[list[ShortText], Field(max_length=20)]
    exclusions: Annotated[list[ShortText], Field(max_length=30)]
    people_count: PositiveCount | None
    set_count: PositiveCount | None
    delivery_date: DateValue | None
    size_requirements: Annotated[list[SizeRequirement], Field(max_length=100)]
    selection_mode: SelectionMode | None
    category_tiers: Annotated[list[CategoryTier], Field(max_length=4)]
    budget_constraints: Annotated[list[BudgetConstraint], Field(max_length=3)]

    @field_validator("delivery_date", mode="before")
    @classmethod
    def delivery_date_must_be_string_or_null(cls, value: object) -> object:
        if value is not None and (
            not isinstance(value, str) or _ISO_DATE.fullmatch(value) is None
        ):
            raise ValueError("delivery_date 必须是 ISO 日期字符串或 null")
        return value

    @field_validator("size_requirements")
    @classmethod
    def size_requirements_must_be_unique(
        cls, value: list[SizeRequirement]
    ) -> list[SizeRequirement]:
        return _validate_size_requirements(value)

    @field_validator("category_tiers")
    @classmethod
    def category_tiers_must_be_ordered(
        cls, value: list[CategoryTier]
    ) -> list[CategoryTier]:
        return _validate_category_tiers(value)

    @field_validator("budget_constraints")
    @classmethod
    def budget_bases_must_be_unique(
        cls, value: list[BudgetConstraint]
    ) -> list[BudgetConstraint]:
        return _validate_budget_constraints(value)

    @model_validator(mode="after")
    def progressive_tiers_must_inherit(self) -> "RequirementDraft":
        _validate_progressive_inheritance(self.selection_mode, self.category_tiers)
        return self


class RequirementAnalysisResult(StrictModel):
    """未来交给 PydanticAI 校验的结构化输出边界。"""

    fact_scope: Literal["requirements_only"]
    human_confirmation_required: Literal[True]
    draft: RequirementDraft
    unresolved_questions: Annotated[list[ShortText], Field(max_length=20)]

    @model_validator(mode="after")
    def incomplete_draft_requires_questions(self) -> "RequirementAnalysisResult":
        missing_blocking_field = (
            self.draft.scheme_name is None
            or self.draft.set_count is None
            or self.draft.selection_mode is None
            or not self.draft.category_tiers
        )
        if missing_blocking_field and not self.unresolved_questions:
            raise ValueError("需求草案缺少阻断字段时必须给出 unresolved_questions")
        return self


class RequirementAnalysisResponse(StrictModel):
    version: Literal["1.0"]
    request_id: UuidValue
    run_id: StableIdentifier
    result: RequirementAnalysisResult


class ProductCurrentAttributes(StrictModel):
    """Java 冻结的当前商品事实；不接收价格、库存、客户或对象存储凭据。"""

    source_ref: StableIdentifier
    sku_ref: StableIdentifier
    name: ShortText
    category_code: ShortText
    color_code: ShortText
    color_name: ShortText
    season: ShortText | None = None
    tags: Annotated[list[ShortText], Field(max_length=50)]


class ProductAttributeInput(StrictModel):
    product_ref: StableIdentifier
    product_row_version: Annotated[StrictInt, Field(ge=1)]
    current_attributes: ProductCurrentAttributes


class ProductAttributeSuggestionRequest(StrictModel):
    version: Literal["1.0"]
    request_id: UuidValue
    correlation_id: StableIdentifier | None = None
    run_id: StableIdentifier
    idempotency_key: IdempotencyKey
    deadline_at: DeadlineValue
    input: ProductAttributeInput

    @field_validator("deadline_at", mode="before")
    @classmethod
    def deadline_must_be_string(cls, value: object) -> object:
        if not isinstance(value, str) or _RFC3339_PREFIX.match(value) is None:
            raise ValueError("deadline_at 必须是 RFC 3339 字符串")
        return value

    @field_validator("deadline_at")
    @classmethod
    def deadline_must_have_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("deadline_at 必须包含时区")
        return value


class ProductAttributeDraft(StrictModel):
    status: Literal["pending_human_confirmation"]
    product_ref: StableIdentifier
    category_code: ShortText | None = None
    color_code: ShortText | None = None
    color_name: ShortText | None = None
    season: ShortText | None = None
    style: ShortText | None = None
    scene: ShortText | None = None
    audience: ShortText | None = None
    observable_tags: Annotated[list[ShortText], Field(max_length=20)]

    @model_validator(mode="after")
    def must_suggest_at_least_one_attribute(self) -> "ProductAttributeDraft":
        if all(
            value is None
            for value in (
                self.category_code,
                self.color_code,
                self.color_name,
                self.season,
                self.style,
                self.scene,
                self.audience,
                *self.observable_tags,
            )
        ):
            raise ValueError("商品属性草稿至少包含一个建议字段")
        return self


class ProductAttributeSuggestionResult(StrictModel):
    fact_scope: Literal["product_attributes_only"]
    human_confirmation_required: Literal[True]
    draft: ProductAttributeDraft
    ambiguities: Annotated[list[ShortText], Field(max_length=20)]


class ProductAttributeSuggestionResponse(StrictModel):
    version: Literal["1.0"]
    request_id: UuidValue
    run_id: StableIdentifier
    result: ProductAttributeSuggestionResult


Sha256Value = Annotated[
    StrictStr,
    Field(pattern=r"^[a-f0-9]{64}$"),
]


class SelectionVariant(StrictModel):
    """候选颜色款内的真实 SKU；引用和版本均来自 Java 冻结快照。"""

    product_ref: StableIdentifier
    product_row_version: Annotated[StrictInt, Field(ge=1)]
    sku_ref: StableIdentifier
    size_code: ShortText
    unit_price_minor: Annotated[StrictInt, Field(ge=0)]
    available_qty: Annotated[StrictInt, Field(ge=0)]


class FrozenSelectionCandidate(StrictModel):
    candidate_ref: StableIdentifier
    category_code: ShortText
    source_ref: StableIdentifier
    style_ref: StableIdentifier
    color_code: ShortText
    color_name: ShortText
    product_name: ShortText
    season: ShortText | None = None
    tags: Annotated[list[ShortText], Field(max_length=50)]
    conservative_unit_price_minor: Annotated[StrictInt, Field(ge=0)]
    total_available_qty: Annotated[StrictInt, Field(ge=1)]
    visual_hash: Sha256Value
    variants: Annotated[list[SelectionVariant], Field(min_length=1, max_length=100)]

    @model_validator(mode="after")
    def variants_must_match_aggregate(self) -> "FrozenSelectionCandidate":
        refs = [variant.product_ref for variant in self.variants]
        if len(refs) != len(set(refs)):
            raise ValueError("候选内 product_ref 不得重复")
        if (
            sum(variant.available_qty for variant in self.variants)
            != self.total_available_qty
        ):
            raise ValueError("候选总库存必须等于 SKU 库存之和")
        if (
            max(variant.unit_price_minor for variant in self.variants)
            != self.conservative_unit_price_minor
        ):
            raise ValueError("候选保守单价必须等于 SKU 最高当前价")
        return self


class SelectionLock(StrictModel):
    slot_index: CategoryCount
    category_code: ShortText
    candidate_ref: StableIdentifier
    candidate_visual_hash: Sha256Value


class SelectionInput(StrictModel):
    quote_ref: StableIdentifier
    quote_row_version: Annotated[StrictInt, Field(ge=1)]
    selection_mode: SelectionMode
    requested_qty: PositiveCount
    budget_maximum_per_set_minor: Annotated[StrictInt, Field(gt=0)] | None = None
    requirements: KnownRequirements
    tiers: Annotated[list[CategoryTier], Field(min_length=1, max_length=4)]
    frozen_candidates: Annotated[
        list[FrozenSelectionCandidate], Field(min_length=1, max_length=200)
    ]
    locks: Annotated[list[SelectionLock], Field(max_length=4)] = Field(
        default_factory=lambda: list[SelectionLock]()
    )
    candidate_set_hash: Sha256Value

    @field_validator("tiers")
    @classmethod
    def tiers_must_be_ordered(cls, value: list[CategoryTier]) -> list[CategoryTier]:
        return _validate_category_tiers(value)

    @model_validator(mode="after")
    def selection_input_must_be_consistent(self) -> "SelectionInput":
        _validate_progressive_inheritance(self.selection_mode, self.tiers)
        candidate_refs = [
            candidate.candidate_ref for candidate in self.frozen_candidates
        ]
        if len(candidate_refs) != len(set(candidate_refs)):
            raise ValueError("frozen_candidates.candidate_ref 不得重复")
        known = {
            candidate.candidate_ref: candidate for candidate in self.frozen_candidates
        }
        lock_slots: set[int] = set()
        for lock in self.locks:
            candidate = known.get(lock.candidate_ref)
            if candidate is None:
                raise ValueError("锁定项必须来自冻结候选集")
            if candidate.category_code != lock.category_code:
                raise ValueError("锁定项品类必须与候选品类一致")
            if candidate.visual_hash != lock.candidate_visual_hash:
                raise ValueError("锁定项视觉摘要必须与冻结候选一致")
            if lock.slot_index in lock_slots:
                raise ValueError("同一 slot_index 不得重复锁定")
            lock_slots.add(lock.slot_index)
        return self


class SelectionStylingRequest(StrictModel):
    version: Literal["1.0"]
    request_id: UuidValue
    correlation_id: StableIdentifier | None = None
    run_id: StableIdentifier
    idempotency_key: IdempotencyKey
    deadline_at: DeadlineValue
    input: SelectionInput

    @field_validator("deadline_at", mode="before")
    @classmethod
    def deadline_must_be_string(cls, value: object) -> object:
        if not isinstance(value, str) or _RFC3339_PREFIX.match(value) is None:
            raise ValueError("deadline_at 必须是 RFC 3339 字符串")
        return value

    @field_validator("deadline_at")
    @classmethod
    def deadline_must_have_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("deadline_at 必须包含时区")
        return value


class SelectionComboItem(StrictModel):
    slot_index: CategoryCount
    category_code: ShortText
    candidate_ref: StableIdentifier


class SelectionComboDraft(StrictModel):
    combo_key: StableIdentifier
    name: ShortText
    reason: Annotated[StrictStr, Field(min_length=1, max_length=2_000)]
    items: Annotated[list[SelectionComboItem], Field(min_length=1, max_length=4)]
    conservative_unit_price_minor: Annotated[StrictInt, Field(ge=0)]
    combo_visual_hash: Sha256Value


class SelectionTierResult(StrictModel):
    category_count: CategoryCount
    requested_candidate_count: CandidateCount
    combinations: Annotated[list[SelectionComboDraft], Field(max_length=3)]
    shortage_reasons: Annotated[list[ShortText], Field(max_length=20)]

    @model_validator(mode="after")
    def shortages_required_when_candidates_are_insufficient(
        self,
    ) -> "SelectionTierResult":
        if len(self.combinations) > self.requested_candidate_count:
            raise ValueError("组合数不得超过 requested_candidate_count")
        if (
            len(self.combinations) < self.requested_candidate_count
            and not self.shortage_reasons
        ):
            raise ValueError("候选不足时必须提供 shortage_reasons")
        return self


class SelectionStylingResult(StrictModel):
    fact_scope: Literal["frozen_candidates_only"]
    human_confirmation_required: Literal[True]
    quote_ref: StableIdentifier
    quote_row_version: Annotated[StrictInt, Field(ge=1)]
    candidate_set_hash: Sha256Value
    tiers: Annotated[list[SelectionTierResult], Field(min_length=1, max_length=4)]


class SelectionStylingResponse(StrictModel):
    version: Literal["1.0"]
    request_id: UuidValue
    run_id: StableIdentifier
    result: SelectionStylingResult


ImageReference = Annotated[
    StrictStr,
    Field(min_length=1, max_length=512, pattern=r"^[A-Za-z0-9][A-Za-z0-9/_.:-]*$"),
]


class ImageSlotInput(StrictModel):
    """仅传给 Provider 的获许可图片引用；不含商品、客户、价格或库存。"""

    slot_code: Annotated[StrictStr, Field(pattern=r"^SLOT-[1-4]$")]
    image_ref: ImageReference
    image_hash: Sha256Value


class ImageGenerationParameters(StrictModel):
    aspect_ratio: Literal["1:1", "3:4", "4:5", "16:9"]
    model_presentation: ShortText | None = None
    scene: ShortText | None = None
    pose: ShortText | None = None
    prompt_version: StableIdentifier
    background: ShortText | None = None
    shadow: StrictBool | None = None


class ImageGenerationInput(StrictModel):
    quote_image_ref: StableIdentifier
    image_type: Literal["model", "styling", "ecommerce"]
    requested_count: Annotated[StrictInt, Field(ge=1, le=4)]
    images: Annotated[list[ImageSlotInput], Field(min_length=1, max_length=4)]
    parameters: ImageGenerationParameters

    @field_validator("images")
    @classmethod
    def slots_must_be_unique(cls, value: list[ImageSlotInput]) -> list[ImageSlotInput]:
        slots = [item.slot_code for item in value]
        if len(slots) != len(set(slots)):
            raise ValueError("图片输入槽位不得重复")
        return value


class ImageGenerationRequest(StrictModel):
    version: Literal["1.0"]
    request_id: UuidValue
    correlation_id: StableIdentifier | None = None
    run_id: StableIdentifier
    idempotency_key: IdempotencyKey
    deadline_at: DeadlineValue
    input: ImageGenerationInput

    @field_validator("deadline_at", mode="before")
    @classmethod
    def deadline_must_be_string(cls, value: object) -> object:
        if not isinstance(value, str) or _RFC3339_PREFIX.match(value) is None:
            raise ValueError("deadline_at 必须是 RFC 3339 字符串")
        return value

    @field_validator("deadline_at")
    @classmethod
    def deadline_must_have_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("deadline_at 必须包含时区")
        return value


ErrorCode = Literal[
    "SERVICE_AUTHENTICATION_FAILED",
    "SERVICE_NOT_AUTHORIZED",
    "DEADLINE_EXCEEDED",
    "IDEMPOTENCY_CONFLICT",
    "VERSION_CONFLICT",
    "FENCING_CONFLICT",
    "LEASE_EXPIRED",
    "RUN_TERMINAL",
    "DELEGATION_EXPIRED",
    "PAYLOAD_TOO_LARGE",
    "REQUEST_VALIDATION_FAILED",
    "BUDGET_EXCEEDED",
    "RATE_LIMITED",
    "PROVIDER_DISABLED",
    "DEPENDENCY_UNAVAILABLE",
    "UPSTREAM_TIMEOUT",
    "INTERNAL_ERROR",
]


class ErrorDetail(StrictModel):
    code: ErrorCode
    message: ErrorMessage
    retryable: bool


class ErrorEnvelope(StrictModel):
    version: Literal["1.0"]
    request_id: UuidValue | None
    correlation_id: StableIdentifier | None
    run_id: StableIdentifier | None
    trace_id: TraceId | None
    error: ErrorDetail
