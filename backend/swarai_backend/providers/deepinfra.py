"""DeepInfra hosted ACE-Step 1.5 XL (https://deepinfra.com/ACE-Step/acestep-v15-xl-sft).

Synchronous inference endpoint: one HTTP call returns the finished audio.
Cancellation can only abandon the HTTP request; DeepInfra may still bill for work already started.
"""

from __future__ import annotations

from typing import Optional

import httpx

from ..schemas import AudioFormat, Capabilities, GenerationRequest, Pricing, ProviderInfo
from .base import (
    Provider,
    ProviderError,
    ProviderResult,
    decode_audio_field,
    raise_for_provider_status,
    sniff_format,
    validate_audio,
)

BASE_URL = "https://api.deepinfra.com/v1/inference/"
MIN_DURATION = 30
MAX_DURATION = 500


class DeepInfraAceStep(Provider):
    def __init__(self, api_key: str, model: str, usd_per_second: float, base_url: str = BASE_URL):
        self._api_key = api_key
        self._url = base_url + model
        self.info = ProviderInfo(
            id="deepinfra-acestep15",
            name="ACE-Step 1.5 XL on DeepInfra",
            model=model,
            hosting="hosted",
            sends_data_to="DeepInfra, Inc. (USA) via api.deepinfra.com",
            license="ACE-Step 1.5 model: MIT. Usage also subject to DeepInfra terms of service.",
            pricing=Pricing(
                usd_per_unit=usd_per_second,
                notes="Billed by DeepInfra per second of generated audio. A 3-minute song is about $0.18 at $0.001/s.",
            ),
            capabilities=Capabilities(
                lyrics_languages="50+ languages incl. Hindi (Devanagari). Bhojpuri/Maithili are sung with Hindi-like pronunciation.",
                min_duration_seconds=MIN_DURATION,
                max_duration_seconds=MAX_DURATION,
                max_variations=4,
                cancel="abandon_only",
                formats=[AudioFormat.mp3, AudioFormat.wav, AudioFormat.flac],
            ),
        )

    def build_payload(self, request: GenerationRequest, seed: Optional[int]) -> dict:
        payload: dict = {
            "prompt": request.caption,
            "lyrics": "" if request.instrumental else request.lyrics,
            "instrumental": request.instrumental,
            "duration": max(MIN_DURATION, min(MAX_DURATION, request.duration_seconds)),
            "response_format": request.audio_format.value,
        }
        if request.negative_prompt:
            payload["negative_prompt"] = request.negative_prompt
        if seed is not None:
            payload["seed"] = seed
        # DeepInfra exposes BPM/key only through the prompt text.
        extras = []
        if request.bpm:
            extras.append(f"{request.bpm} BPM")
        if request.key_scale:
            extras.append(request.key_scale)
        if extras:
            payload["prompt"] = f"{request.caption}, {', '.join(extras)}"
        return payload

    async def generate(
        self, request: GenerationRequest, seed: Optional[int], client: httpx.AsyncClient
    ) -> ProviderResult:
        response = await client.post(
            self._url,
            json=self.build_payload(request, seed),
            headers={"Authorization": f"Bearer {self._api_key}"},
        )
        raise_for_provider_status(response, "DeepInfra")
        try:
            body = response.json()
        except ValueError as exc:
            raise ProviderError("DeepInfra returned a non-JSON response.") from exc
        status = (body.get("inference_status") or {}).get("status")
        if status == "failed":
            raise ProviderError("DeepInfra reported that generation failed.")
        audio = await decode_audio_field(body.get("audio"), client)
        validate_audio(audio, "DeepInfra")
        returned_seed = body.get("seed")
        return ProviderResult(
            audio=audio,
            format=sniff_format(audio, body.get("output_format") or request.audio_format.value),
            seed=int(returned_seed) if isinstance(returned_seed, (int, float)) else seed,
            duration_seconds=body.get("duration_seconds"),
            lyrics_used=body.get("generated_lyrics") or None,
        )
