from __future__ import annotations

from ..config import Settings
from .acestep import SelfHostedAceStep
from .base import Provider, ProviderError, ProviderResult
from .deepinfra import DeepInfraAceStep
from .diagnostic import DiagnosticTone
from .fal import FalAceStep

__all__ = ["Provider", "ProviderError", "ProviderResult", "build_providers"]


def build_providers(settings: Settings) -> dict[str, Provider]:
    """Only providers whose credentials/URLs are configured are exposed."""
    providers: list[Provider] = []
    if settings.deepinfra_api_key:
        providers.append(
            DeepInfraAceStep(settings.deepinfra_api_key, settings.deepinfra_model, settings.deepinfra_usd_per_second)
        )
    if settings.fal_key:
        providers.append(FalAceStep(settings.fal_key, settings.fal_model, settings.fal_usd_per_second))
    if settings.acestep_url:
        providers.append(SelfHostedAceStep(settings.acestep_url, settings.acestep_api_key))
    if settings.enable_diagnostic:
        providers.append(DiagnosticTone())
    return {p.info.id: p for p in providers}
