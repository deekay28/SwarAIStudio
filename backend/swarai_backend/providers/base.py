"""Provider abstraction. Each adapter turns one GenerationRequest variation into audio bytes."""

from __future__ import annotations

import base64
import binascii
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional

import httpx

from ..schemas import GenerationRequest, ProviderInfo


class ProviderError(Exception):
    """A provider failure with a message that is safe to show to the user."""

    def __init__(self, message: str, *, retryable: bool = False):
        super().__init__(message)
        self.retryable = retryable


@dataclass
class ProviderResult:
    audio: bytes
    format: str
    seed: Optional[int] = None
    duration_seconds: Optional[float] = None
    lyrics_used: Optional[str] = None


class Provider(ABC):
    info: ProviderInfo

    @abstractmethod
    async def generate(
        self, request: GenerationRequest, seed: Optional[int], client: httpx.AsyncClient
    ) -> ProviderResult:
        """Generate one variation. Must clean up remote work if cancelled (asyncio.CancelledError)."""


def raise_for_provider_status(response: httpx.Response, provider_name: str) -> None:
    if response.is_success:
        return
    status = response.status_code
    if status in (401, 403):
        raise ProviderError(f"{provider_name} rejected the server's credentials (HTTP {status}).")
    if status == 402:
        raise ProviderError(f"{provider_name} reports insufficient credit or billing not set up.")
    if status == 422 or status == 400:
        detail = _short_detail(response)
        raise ProviderError(f"{provider_name} rejected the request parameters: {detail}")
    if status == 429:
        raise ProviderError(f"{provider_name} is rate limiting or busy. Try again shortly.", retryable=True)
    raise ProviderError(f"{provider_name} failed (HTTP {status}).", retryable=status >= 500)


def _short_detail(response: httpx.Response) -> str:
    try:
        body = response.json()
        detail = body.get("detail") or body.get("error") or body
    except ValueError:
        detail = response.text
    return str(detail)[:300]


async def decode_audio_field(value: object, client: httpx.AsyncClient) -> bytes:
    """Accept the shapes hosted APIs use for audio: data URI, bare base64, URL, or {"url": ...}."""
    if isinstance(value, dict):
        value = value.get("url") or value.get("data") or value.get("audio")
    if not isinstance(value, str) or not value:
        raise ProviderError("Provider returned no audio.")
    if value.startswith("https://") or value.startswith("http://"):
        response = await client.get(value, follow_redirects=True)
        if not response.is_success:
            raise ProviderError(f"Could not download generated audio (HTTP {response.status_code}).")
        return response.content
    if value.startswith("data:"):
        _, _, value = value.partition(",")
    try:
        return base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ProviderError("Provider returned audio in an unrecognised encoding.") from exc


def sniff_format(audio: bytes, fallback: str) -> str:
    if audio[:4] == b"RIFF" and audio[8:12] == b"WAVE":
        return "wav"
    if audio[:4] == b"fLaC":
        return "flac"
    if audio[:3] == b"ID3" or (len(audio) > 1 and audio[0] == 0xFF and (audio[1] & 0xE0) == 0xE0):
        return "mp3"
    if audio[:4] == b"OggS":
        return "ogg"
    return fallback


def validate_audio(audio: bytes, provider_name: str) -> None:
    if len(audio) < 1024:
        raise ProviderError(f"{provider_name} returned an unexpectedly small audio file.")
