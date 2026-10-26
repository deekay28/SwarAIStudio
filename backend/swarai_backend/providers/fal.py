"""fal.ai hosted ACE-Step (https://fal.ai/models/fal-ai/ace-step) via the fal queue API.

The queue API supports real cancellation through the request's cancel_url.
"""

from __future__ import annotations

import asyncio
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

QUEUE_URL = "https://queue.fal.run/"
MAX_DURATION = 240


class FalAceStep(Provider):
    def __init__(self, api_key: str, model: str, usd_per_second: float, poll_interval: float = 2.0,
                 queue_url: str = QUEUE_URL):
        self._api_key = api_key
        self._submit_url = queue_url + model
        self._poll_interval = poll_interval
        self.info = ProviderInfo(
            id="fal-acestep",
            name="ACE-Step on fal.ai",
            model=model,
            hosting="hosted",
            sends_data_to="Features & Labels, Inc. (fal.ai, USA) via queue.fal.run",
            license="ACE-Step v1 model: Apache-2.0. Usage also subject to fal.ai terms.",
            pricing=Pricing(
                usd_per_unit=usd_per_second,
                notes="Billed by fal.ai per second of generated audio. Check fal.ai pricing before use.",
            ),
            capabilities=Capabilities(
                lyrics_languages="Multilingual (strongest in English/Chinese); Hindi quality varies.",
                min_duration_seconds=10,
                max_duration_seconds=MAX_DURATION,
                max_variations=4,
                cancel="supported",
                formats=[AudioFormat.wav],
            ),
        )

    @property
    def _headers(self) -> dict:
        return {"Authorization": f"Key {self._api_key}"}

    def build_payload(self, request: GenerationRequest, seed: Optional[int]) -> dict:
        payload: dict = {
            "tags": request.caption,
            "lyrics": "[inst]" if request.instrumental or not request.lyrics else request.lyrics,
            "duration": min(MAX_DURATION, request.duration_seconds),
        }
        if seed is not None:
            payload["seed"] = seed
        return payload

    async def generate(
        self, request: GenerationRequest, seed: Optional[int], client: httpx.AsyncClient
    ) -> ProviderResult:
        submit = await client.post(self._submit_url, json=self.build_payload(request, seed), headers=self._headers)
        raise_for_provider_status(submit, "fal.ai")
        queued = submit.json()
        status_url = queued.get("status_url")
        response_url = queued.get("response_url")
        cancel_url = queued.get("cancel_url")
        if not status_url or not response_url:
            raise ProviderError("fal.ai did not return a queue handle.")
        try:
            while True:
                status = await client.get(status_url, headers=self._headers)
                raise_for_provider_status(status, "fal.ai")
                state = status.json().get("status")
                if state == "COMPLETED":
                    break
                if state not in ("IN_QUEUE", "IN_PROGRESS"):
                    raise ProviderError(f"fal.ai job ended with status {state}.")
                await asyncio.sleep(self._poll_interval)
        except asyncio.CancelledError:
            if cancel_url:
                try:
                    await asyncio.shield(client.put(cancel_url, headers=self._headers))
                except Exception:  # noqa: BLE001 - best effort during cancellation
                    pass
            raise
        result = await client.get(response_url, headers=self._headers)
        raise_for_provider_status(result, "fal.ai")
        body = result.json()
        audio = await decode_audio_field(body.get("audio"), client)
        validate_audio(audio, "fal.ai")
        return ProviderResult(
            audio=audio,
            format=sniff_format(audio, "wav"),
            seed=body.get("seed", seed),
            lyrics_used=body.get("lyrics"),
        )
