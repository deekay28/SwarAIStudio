"""NOT an AI model. Produces a short WAV test tone so a deployment can be verified at zero cost.

Disabled unless SWARAI_ENABLE_DIAGNOSTIC=1. The app labels it as a diagnostic, never as music generation.
"""

from __future__ import annotations

import io
import math
import struct
import wave
from typing import Optional

import httpx

from ..schemas import AudioFormat, Capabilities, GenerationRequest, Pricing, ProviderInfo
from .base import Provider, ProviderResult

SAMPLE_RATE = 22050


class DiagnosticTone(Provider):
    info = ProviderInfo(
        id="diagnostic-tone",
        name="Diagnostic test tone (not AI)",
        model="sine-wave generator",
        hosting="self_hosted",
        sends_data_to="Only this SwarAI backend",
        license="Generated tone, no restrictions.",
        is_ai_music=False,
        pricing=Pricing(usd_per_unit=0.0, notes="Free. Verifies connectivity only; produces no music."),
        capabilities=Capabilities(
            vocals=False,
            lyrics_languages="Ignores lyrics.",
            max_duration_seconds=600,
            cancel="supported",
            formats=[AudioFormat.wav],
        ),
    )

    async def generate(
        self, request: GenerationRequest, seed: Optional[int], client: httpx.AsyncClient
    ) -> ProviderResult:
        seconds = min(request.duration_seconds, 15)
        return ProviderResult(audio=tone_wav(seconds, 440.0 + (seed or 0) % 220), format="wav", seed=seed,
                              duration_seconds=float(seconds))


def tone_wav(seconds: int, frequency: float) -> bytes:
    frames = bytearray()
    total = SAMPLE_RATE * seconds
    for n in range(total):
        envelope = min(1.0, n / 2000, (total - n) / 2000)
        frames += struct.pack("<h", int(12000 * envelope * math.sin(2 * math.pi * frequency * n / SAMPLE_RATE)))
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(SAMPLE_RATE)
        out.writeframes(bytes(frames))
    return buffer.getvalue()
