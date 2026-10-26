"""In-memory job store. Audio lives in a private temp dir and is deleted after the TTL or on request.

This is intentionally single-process: run one instance (e.g. Cloud Run max-instances=1).
"""

from __future__ import annotations

import asyncio
import logging
import random
import secrets
import shutil
import tempfile
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import httpx

from .providers import Provider, ProviderError
from .schemas import GenerationRequest, JobInfo, JobStatus, OutputInfo

log = logging.getLogger("swarai.jobs")


@dataclass
class Output:
    index: int
    seed: Optional[int]
    status: JobStatus = JobStatus.queued
    path: Optional[Path] = None
    format: Optional[str] = None
    duration_seconds: Optional[float] = None
    lyrics_used: Optional[str] = None
    error: Optional[str] = None


@dataclass
class Job:
    id: str
    owner: str
    provider: Provider
    request: GenerationRequest
    estimated_cost_usd: float
    created_at: float = field(default_factory=time.time)
    outputs: list[Output] = field(default_factory=list)
    task: Optional[asyncio.Task] = None
    cancelled: bool = False

    @property
    def status(self) -> JobStatus:
        states = {o.status for o in self.outputs}
        if self.cancelled:
            return JobStatus.cancelled
        if states & {JobStatus.queued, JobStatus.running}:
            return JobStatus.running if JobStatus.running in states or JobStatus.succeeded in states else JobStatus.queued
        if JobStatus.succeeded in states:
            return JobStatus.succeeded
        return JobStatus.failed

    def to_info(self) -> JobInfo:
        errors = [o.error for o in self.outputs if o.error]
        return JobInfo(
            id=self.id,
            provider=self.provider.info.id,
            status=self.status,
            created_at=self.created_at,
            estimated_cost_usd=self.estimated_cost_usd,
            error=errors[0] if errors and self.status == JobStatus.failed else None,
            outputs=[
                OutputInfo(
                    index=o.index,
                    status=o.status,
                    audio_path=f"/v1/generations/{self.id}/outputs/{o.index}" if o.path else None,
                    format=o.format,
                    size_bytes=o.path.stat().st_size if o.path and o.path.exists() else None,
                    duration_seconds=o.duration_seconds,
                    seed=o.seed,
                    lyrics_used=o.lyrics_used,
                    error=o.error,
                )
                for o in self.outputs
            ],
        )


class JobStore:
    def __init__(self, client: httpx.AsyncClient, ttl_seconds: int, timeout_seconds: float):
        self._client = client
        self._ttl = ttl_seconds
        self._timeout = timeout_seconds
        self._jobs: dict[str, Job] = {}
        self._dir = Path(tempfile.mkdtemp(prefix="swarai-"))

    def create(self, owner: str, provider: Provider, request: GenerationRequest, cost: float) -> Job:
        self.purge_expired()
        job = Job(id=secrets.token_urlsafe(12), owner=owner, provider=provider, request=request,
                  estimated_cost_usd=cost)
        base_seed = request.seed
        for i in range(request.variations):
            seed = base_seed + i if base_seed is not None else random.randint(0, 2**31 - 1)
            job.outputs.append(Output(index=i, seed=seed))
        self._jobs[job.id] = job
        job.task = asyncio.create_task(self._run(job))
        log.info("job %s created provider=%s variations=%d lyrics_chars=%d", job.id, provider.info.id,
                 request.variations, len(request.lyrics))
        return job

    def get(self, job_id: str, owner: str) -> Optional[Job]:
        job = self._jobs.get(job_id)
        return job if job and secrets.compare_digest(job.owner, owner) else None

    async def cancel(self, job: Job) -> None:
        job.cancelled = True
        if job.task and not job.task.done():
            job.task.cancel()
            try:
                await job.task
            except asyncio.CancelledError:
                pass
        for out in job.outputs:
            if out.status in (JobStatus.queued, JobStatus.running):
                out.status = JobStatus.cancelled

    async def delete(self, job: Job) -> None:
        await self.cancel(job)
        shutil.rmtree(self._dir / job.id, ignore_errors=True)
        self._jobs.pop(job.id, None)

    def purge_expired(self) -> None:
        now = time.time()
        for job in [j for j in self._jobs.values() if now - j.created_at > self._ttl]:
            if job.task and not job.task.done():
                job.task.cancel()
            shutil.rmtree(self._dir / job.id, ignore_errors=True)
            self._jobs.pop(job.id, None)

    async def close(self) -> None:
        for job in list(self._jobs.values()):
            await self.cancel(job)
        shutil.rmtree(self._dir, ignore_errors=True)

    async def _run(self, job: Job) -> None:
        await asyncio.gather(*(self._run_output(job, out) for out in job.outputs))

    async def _run_output(self, job: Job, out: Output) -> None:
        out.status = JobStatus.running
        try:
            result = await asyncio.wait_for(
                job.provider.generate(job.request, out.seed, self._client), timeout=self._timeout
            )
        except asyncio.CancelledError:
            out.status = JobStatus.cancelled
            raise
        except asyncio.TimeoutError:
            out.status, out.error = JobStatus.failed, "The provider did not finish in time."
            return
        except ProviderError as exc:
            out.status, out.error = JobStatus.failed, str(exc)
            log.warning("job %s output %d failed: %s", job.id, out.index, exc)
            return
        except httpx.HTTPError as exc:
            out.status, out.error = JobStatus.failed, f"Network error talking to the provider ({type(exc).__name__})."
            return
        folder = self._dir / job.id
        folder.mkdir(parents=True, exist_ok=True)
        path = folder / f"{out.index}.{result.format}"
        path.write_bytes(result.audio)
        out.path, out.format = path, result.format
        out.seed = result.seed if result.seed is not None else out.seed
        out.duration_seconds = result.duration_seconds
        out.lyrics_used = result.lyrics_used
        out.status = JobStatus.succeeded
