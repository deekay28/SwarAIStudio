"""Settings are read from environment variables only, never from files in the repo."""

from __future__ import annotations

import os
from dataclasses import dataclass, field


def _env_list(name: str) -> list[str]:
    raw = os.environ.get(name, "")
    return [item.strip() for item in raw.split(",") if item.strip()]


def _env_float(name: str, default: float) -> float:
    raw = os.environ.get(name)
    return float(raw) if raw else default


def _env_int(name: str, default: int) -> int:
    raw = os.environ.get(name)
    return int(raw) if raw else default


@dataclass
class Settings:
    # Bearer tokens the Android app may present. Generate with `openssl rand -hex 32`.
    app_tokens: list[str] = field(default_factory=lambda: _env_list("SWARAI_APP_TOKENS"))
    # Provider credentials (server-side only).
    deepinfra_api_key: str = field(default_factory=lambda: os.environ.get("DEEPINFRA_API_KEY", ""))
    deepinfra_model: str = field(
        default_factory=lambda: os.environ.get("DEEPINFRA_MODEL", "ACE-Step/acestep-v15-xl-sft")
    )
    # Price per second of generated audio, used for estimates. Update if the provider changes it.
    deepinfra_usd_per_second: float = field(
        default_factory=lambda: _env_float("DEEPINFRA_USD_PER_SECOND", 0.001)
    )
    fal_key: str = field(default_factory=lambda: os.environ.get("FAL_KEY", ""))
    fal_model: str = field(default_factory=lambda: os.environ.get("FAL_MODEL", "fal-ai/ace-step"))
    fal_usd_per_second: float = field(default_factory=lambda: _env_float("FAL_USD_PER_SECOND", 0.0002))
    # Self-hosted ACE-Step 1.5 REST API server (`uv run acestep-api`).
    acestep_url: str = field(default_factory=lambda: os.environ.get("ACESTEP_API_URL", ""))
    acestep_api_key: str = field(default_factory=lambda: os.environ.get("ACESTEP_API_KEY", ""))
    # A non-AI test tone provider for verifying a deployment for free. Off by default.
    enable_diagnostic: bool = field(
        default_factory=lambda: os.environ.get("SWARAI_ENABLE_DIAGNOSTIC", "") == "1"
    )
    default_provider: str = field(default_factory=lambda: os.environ.get("SWARAI_DEFAULT_PROVIDER", ""))
    # Cost guard: total seconds of audio that may be requested per UTC day across all tokens.
    max_daily_audio_seconds: int = field(
        default_factory=lambda: _env_int("SWARAI_MAX_DAILY_AUDIO_SECONDS", 1800)
    )
    provider_timeout_seconds: float = field(
        default_factory=lambda: _env_float("SWARAI_PROVIDER_TIMEOUT_SECONDS", 600)
    )
    # Generated audio is deleted from the server after this many seconds (or on DELETE).
    result_ttl_seconds: int = field(default_factory=lambda: _env_int("SWARAI_RESULT_TTL_SECONDS", 3600))
