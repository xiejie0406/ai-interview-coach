"""运行时对外部能力的端口。"""

from fashion_ai.ports.product_attribute_suggester import ProductAttributeSuggester
from fashion_ai.ports.requirement_analyzer import RequirementAnalyzer
from fashion_ai.ports.selection_ranker import SelectionRanker
from fashion_ai.ports.service_auth import (
    AuthenticatedService,
    NonceClaimResult,
    NonceStore,
    ServiceAuthenticator,
    ServiceRequestSigner,
    ServiceRequestToSign,
    SignedServiceRequest,
)

__all__ = [
    "AuthenticatedService",
    "NonceClaimResult",
    "NonceStore",
    "ProductAttributeSuggester",
    "RequirementAnalyzer",
    "SelectionRanker",
    "ServiceAuthenticator",
    "ServiceRequestSigner",
    "ServiceRequestToSign",
    "SignedServiceRequest",
]
