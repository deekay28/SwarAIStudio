"""Self-hosted ACE-Step 1.5 REST API (`uv run acestep-api`), e.g. on your own GPU PC or a rented GPU.

API reference: https://github.com/ace-step/ACE-Step-1.5/blob/main/docs/en/API.md
"""

from __future__ import annotations

import asyncio
import json
import re
from typing import Optional
from urllib.parse import urljoin

import httpx

from ..schemas import AudioFormat, Capabilities, GenerationRequest, Pricing, ProviderInfo
from .base import (
    Provider,
    ProviderError,
    ProviderResult,
    raise_for_provider_status,
    sniff_format,
    validate_audio,
)


MAX_TRANSIENT_FAILURES = 10


class SelfHostedAceStep(Provider):
    def __init__(self, base_url: str, api_key: str = "", poll_interval: float = 2.0):
        self._base = base_url.rstrip("/") + "/"
        self._api_key = api_key
        self._poll_interval = poll_interval
        self.info = ProviderInfo(
            id="acestep-selfhosted",
            name="ACE-Step 1.5 (self-hosted)",
            model="ACE-Step 1.5 (model chosen on the server)",
            hosting="self_hosted",
            sends_data_to=f"Your own ACE-Step server at {base_url}",
            license="ACE-Step 1.5: MIT (code and weights).",
            pricing=Pricing(usd_per_unit=0.0, notes="No per-song fee; you pay for your own hardware/electricity or GPU rental."),
            capabilities=Capabilities(
                lyrics_languages="50+ languages incl. Hindi (Devanagari).",
                min_duration_seconds=10,
                max_duration_seconds=600,
                max_variations=4,
                cancel="abandon_only",
                formats=[AudioFormat.mp3, AudioFormat.wav, AudioFormat.flac],
                edit_operations=["cover", "repaint"],
            ),
        )

    @property
    def _headers(self) -> dict:
        return {"Authorization": f"Bearer {self._api_key}"} if self._api_key else {}

    def build_payload(self, request: GenerationRequest, seed: Optional[int]) -> dict:
        payload: dict = {
            "prompt": request.caption,
            "lyrics": "[Instrumental]" if request.instrumental else request.lyrics,
            "vocal_language": request.language,
            "audio_duration": request.duration_seconds,
            "audio_format": request.audio_format.value,
            "batch_size": 1,
            "thinking": True,
        }
        if request.bpm:
            payload["bpm"] = request.bpm
        if request.key_scale:
            payload["key_scale"] = request.key_scale
        if seed is not None:
            payload["use_random_seed"] = False
            payload["seed"] = seed
        return payload

    async def generate(
        self, request: GenerationRequest, seed: Optional[int], client: httpx.AsyncClient
    ) -> ProviderResult:
        payload = self.build_payload(request, seed)
        # The server rejects thinking=true when its planner LM isn't loaded; only request it when available.
        try:
            health = await client.get(urljoin(self._base, "health"), headers=self._headers)
            if health.is_success and (health.json().get("data") or {}).get("llm_initialized") is False:
                payload["thinking"] = False
        except (httpx.HTTPError, ValueError):
            pass
        release = await client.post(urljoin(self._base, "release_task"), json=payload, headers=self._headers)
        raise_for_provider_status(release, "ACE-Step server")
        task_id = (release.json().get("data") or {}).get("task_id")
        if not task_id:
            raise ProviderError("ACE-Step server did not return a task id.")
        transient = 0
        while True:
            try:
                query = await client.post(
                    urljoin(self._base, "query_result"), json={"task_id_list": [task_id]}, headers=self._headers
                )
            except httpx.TransportError:
                query = None
            # Tunnels drop the odd request (e.g. Cloudflare 530); only give up after repeated failures.
            if query is None or query.status_code >= 500:
                transient += 1
                if transient > MAX_TRANSIENT_FAILURES:
                    if query is None:
                        raise ProviderError("Lost connection to the ACE-Step server.", retryable=True)
                    raise_for_provider_status(query, "ACE-Step server")
                await asyncio.sleep(self._poll_interval * min(transient, 5))
                continue
            transient = 0
            raise_for_provider_status(query, "ACE-Step server")
            items = query.json().get("data") or []
            item = next((i for i in items if i.get("task_id") == task_id), None)
            if item and item.get("status") == 1:
                break
            if item and item.get("status") == 2:
                raise ProviderError(failure_message(item))
            await asyncio.sleep(self._poll_interval)
        results = parse_result_field(item.get("result"))
        if not results or not results[0].get("file"):
            raise ProviderError("ACE-Step server returned no audio file.")
        first = results[0]
        audio_response = await client.get(urljoin(self._base, first["file"].lstrip("/")), headers=self._headers)
        raise_for_provider_status(audio_response, "ACE-Step server")
        audio = audio_response.content
        validate_audio(audio, "ACE-Step server")
        metas = first.get("metas") or {}
        seed_value = str(first.get("seed_value") or "").split(",")[0].strip()
        return ProviderResult(
            audio=audio,
            format=sniff_format(audio, request.audio_format.value),
            seed=int(seed_value) if seed_value.isdigit() else seed,
            duration_seconds=metas.get("duration"),
            lyrics_used=first.get("lyrics"),
        )


def failure_message(item: dict) -> str:
    """Short reason from ACE-Step's failure details (usually a Python traceback)."""
    raw = next((r.get("error") for r in parse_result_field(item.get("result")) if r.get("error")), None) \
        or item.get("progress_text") or ""
    lines = [line.strip() for line in str(raw).splitlines() if line.strip()]
    cause = next((line for line in reversed(lines) if re.match(r"^[\w.]*(Error|Exception)\b", line)), lines[-1] if lines else "")
    hint = " The GPU ran out of memory: try a shorter song." if "out of memory" in str(raw).lower() else ""
    return (f"ACE-Step generation failed: {cause[:300]}" if cause else "ACE-Step server reported that generation failed.") + hint


def parse_result_field(result: object) -> list[dict]:
    if isinstance(result, str):
        try:
            result = json.loads(result)
        except ValueError:
            return []
    if isinstance(result, dict):
        return [result]
    return [r for r in result or [] if isinstance(r, dict)]
