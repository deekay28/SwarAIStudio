"""SwarAI Studio backend API.

Run locally:  uvicorn swarai_backend.main:app --host 0.0.0.0 --port 8080
"""

from __future__ import annotations

import logging
import time
from contextlib import asynccontextmanager
from typing import Optional

import httpx
from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.responses import FileResponse

from . import __version__
from .auth import require_token
from .config import Settings
from .jobs import JobStore
from .providers import Provider, build_providers
from .schemas import Estimate, GenerationRequest, JobInfo, JobStatus, ProviderInfo

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

MEDIA_TYPES = {"mp3": "audio/mpeg", "wav": "audio/wav", "flac": "audio/flac", "ogg": "audio/ogg"}


class DailyBudget:
    """Caps total requested audio seconds per UTC day: a simple guard against runaway provider bills."""

    def __init__(self, limit_seconds: int):
        self.limit = limit_seconds
        self._day = self._today()
        self.used = 0

    @staticmethod
    def _today() -> int:
        return int(time.time() // 86400)

    def remaining(self) -> int:
        if self._today() != self._day:
            self._day, self.used = self._today(), 0
        return max(0, self.limit - self.used)

    def reserve(self, seconds: int) -> bool:
        if seconds > self.remaining():
            return False
        self.used += seconds
        return True

    def refund(self, seconds: int) -> None:
        self.used = max(0, self.used - seconds)


def create_app(settings: Optional[Settings] = None, transport: Optional[httpx.AsyncBaseTransport] = None) -> FastAPI:
    settings = settings or Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        client = httpx.AsyncClient(timeout=httpx.Timeout(settings.provider_timeout_seconds, connect=20),
                                   transport=transport)
        app.state.jobs = JobStore(client, settings.result_ttl_seconds, settings.provider_timeout_seconds)
        yield
        await app.state.jobs.close()
        await client.aclose()

    app = FastAPI(title="SwarAI Studio backend", version=__version__, lifespan=lifespan)
    app.state.settings = settings
    app.state.providers = build_providers(settings)
    app.state.budget = DailyBudget(settings.max_daily_audio_seconds)

    def pick_provider(provider_id: Optional[str]) -> Provider:
        providers: dict[str, Provider] = app.state.providers
        if not providers:
            raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "No music provider is configured on the server.")
        chosen = provider_id or settings.default_provider or next(iter(providers))
        if chosen not in providers:
            raise HTTPException(status.HTTP_404_NOT_FOUND, f"Unknown or unconfigured provider '{chosen}'.")
        return providers[chosen]

    def audio_seconds(provider: Provider, req: GenerationRequest) -> int:
        caps = provider.info.capabilities
        per = max(caps.min_duration_seconds, min(caps.max_duration_seconds, req.duration_seconds))
        return per * req.variations

    def check_capabilities(provider: Provider, req: GenerationRequest) -> None:
        caps = provider.info.capabilities
        if req.variations > caps.max_variations:
            raise HTTPException(422, f"{provider.info.name} supports at most {caps.max_variations} variations.")
        if req.audio_format not in caps.formats:
            allowed = ", ".join(f.value for f in caps.formats)
            raise HTTPException(422, f"{provider.info.name} supports formats: {allowed}.")
        if not req.instrumental and not req.lyrics and caps.vocals:
            raise HTTPException(422, "Lyrics are required unless 'instrumental' is set.")

    @app.get("/health")
    async def health() -> dict:
        return {"status": "ok", "version": __version__, "providers_configured": len(app.state.providers)}

    @app.get("/v1/providers", response_model=list[ProviderInfo])
    async def providers(_: str = Depends(require_token)) -> list[ProviderInfo]:
        return [p.info for p in app.state.providers.values()]

    @app.post("/v1/estimate", response_model=Estimate)
    async def estimate(req: GenerationRequest, _: str = Depends(require_token)) -> Estimate:
        provider = pick_provider(req.provider)
        seconds = audio_seconds(provider, req)
        return Estimate(
            provider=provider.info.id,
            audio_seconds=seconds,
            estimated_cost_usd=round(seconds * provider.info.pricing.usd_per_unit, 4),
            pricing=provider.info.pricing,
            remaining_daily_audio_seconds=app.state.budget.remaining(),
        )

    @app.post("/v1/generations", response_model=JobInfo, status_code=status.HTTP_202_ACCEPTED)
    async def create_generation(req: GenerationRequest, request: Request, owner: str = Depends(require_token)) -> JobInfo:
        provider = pick_provider(req.provider)
        check_capabilities(provider, req)
        seconds = audio_seconds(provider, req)
        if not app.state.budget.reserve(seconds):
            raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS,
                                "Daily audio budget on this server is used up. Raise SWARAI_MAX_DAILY_AUDIO_SECONDS to allow more.")
        cost = round(seconds * provider.info.pricing.usd_per_unit, 4)
        job = request.app.state.jobs.create(owner, provider, req, cost)
        return job.to_info()

    def owned_job(request: Request, job_id: str, owner: str):
        job = request.app.state.jobs.get(job_id, owner)
        if not job:
            raise HTTPException(status.HTTP_404_NOT_FOUND, "Job not found (it may have expired).")
        return job

    @app.get("/v1/generations/{job_id}", response_model=JobInfo)
    async def get_generation(job_id: str, request: Request, owner: str = Depends(require_token)) -> JobInfo:
        return owned_job(request, job_id, owner).to_info()

    @app.get("/v1/generations/{job_id}/outputs/{index}")
    async def get_output(job_id: str, index: int, request: Request, owner: str = Depends(require_token)):
        job = owned_job(request, job_id, owner)
        if index < 0 or index >= len(job.outputs) or not job.outputs[index].path:
            raise HTTPException(status.HTTP_404_NOT_FOUND, "Output not ready.")
        out = job.outputs[index]
        return FileResponse(out.path, media_type=MEDIA_TYPES.get(out.format or "", "application/octet-stream"),
                            filename=f"swarai-{job_id}-{index}.{out.format}")

    @app.post("/v1/generations/{job_id}/cancel", response_model=JobInfo)
    async def cancel_generation(job_id: str, request: Request, owner: str = Depends(require_token)) -> JobInfo:
        job = owned_job(request, job_id, owner)
        unfinished = sum(1 for o in job.outputs if o.status in (JobStatus.queued, JobStatus.running))
        await request.app.state.jobs.cancel(job)
        # Only refund seconds that were not produced. Provider-side billing may still apply.
        per = audio_seconds(job.provider, job.request) // max(1, job.request.variations)
        app.state.budget.refund(per * unfinished)
        return job.to_info()

    @app.delete("/v1/generations/{job_id}", status_code=status.HTTP_204_NO_CONTENT)
    async def delete_generation(job_id: str, request: Request, owner: str = Depends(require_token)) -> None:
        await request.app.state.jobs.delete(owned_job(request, job_id, owner))

    return app


app = create_app()
