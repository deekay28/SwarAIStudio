import base64
import time

import httpx
import pytest
from fastapi.testclient import TestClient

from swarai_backend.config import Settings
from swarai_backend.main import create_app

TOKEN = "test-token"
AUTH = {"Authorization": f"Bearer {TOKEN}"}
FAKE_MP3 = b"ID3" + b"\x01" * 5000


def deepinfra_ok(request: httpx.Request):
    return httpx.Response(200, json={"audio": base64.b64encode(FAKE_MP3).decode(), "seed": 1})


def make_client(handler=deepinfra_ok, **settings_kw):
    settings = Settings(app_tokens=[TOKEN], deepinfra_api_key="dk", **settings_kw)
    return TestClient(create_app(settings, transport=httpx.MockTransport(handler)))


BODY = {"caption": "hindi film song, male vocal", "lyrics": "तुम ही हो", "duration_seconds": 60, "variations": 2}


def wait_done(client, job_id):
    for _ in range(100):
        info = client.get(f"/v1/generations/{job_id}", headers=AUTH).json()
        if info["status"] not in ("queued", "running"):
            return info
        time.sleep(0.02)
    raise AssertionError("job did not finish")


def test_health_is_public_but_api_requires_token():
    with make_client() as client:
        assert client.get("/health").json()["status"] == "ok"
        assert client.get("/v1/providers").status_code == 401
        assert client.get("/v1/providers", headers={"Authorization": "Bearer nope"}).status_code == 401
        providers = client.get("/v1/providers", headers=AUTH).json()
        assert [p["id"] for p in providers] == ["deepinfra-acestep15"]
        assert "dk" not in str(providers)


def test_estimate_reports_cost():
    with make_client() as client:
        est = client.post("/v1/estimate", json=BODY, headers=AUTH).json()
        assert est["audio_seconds"] == 120 and est["estimated_cost_usd"] == pytest.approx(0.12)


def test_generation_end_to_end_and_delete():
    with make_client() as client:
        job = client.post("/v1/generations", json=BODY, headers=AUTH)
        assert job.status_code == 202
        info = wait_done(client, job.json()["id"])
        assert info["status"] == "succeeded" and len(info["outputs"]) == 2
        audio = client.get(info["outputs"][1]["audio_path"], headers=AUTH)
        assert audio.content == FAKE_MP3 and audio.headers["content-type"] == "audio/mpeg"
        assert client.delete(f"/v1/generations/{info['id']}", headers=AUTH).status_code == 204
        assert client.get(f"/v1/generations/{info['id']}", headers=AUTH).status_code == 404


def test_jobs_are_private_to_their_token():
    settings = Settings(app_tokens=[TOKEN, "other"], deepinfra_api_key="dk")
    with TestClient(create_app(settings, transport=httpx.MockTransport(deepinfra_ok))) as client:
        job_id = client.post("/v1/generations", json=BODY, headers=AUTH).json()["id"]
        assert client.get(f"/v1/generations/{job_id}", headers={"Authorization": "Bearer other"}).status_code == 404


def test_provider_failure_is_reported():
    with make_client(lambda r: httpx.Response(402, json={})) as client:
        info = wait_done(client, client.post("/v1/generations", json=BODY, headers=AUTH).json()["id"])
        assert info["status"] == "failed" and "credit" in info["error"]


def test_daily_budget_blocks_overspend():
    with make_client(max_daily_audio_seconds=100) as client:
        r = client.post("/v1/generations", json=BODY, headers=AUTH)
        assert r.status_code == 429


def test_validation_errors():
    with make_client() as client:
        assert client.post("/v1/generations", json={**BODY, "lyrics": ""}, headers=AUTH).status_code == 422
        assert client.post("/v1/generations", json={**BODY, "variations": 9}, headers=AUTH).status_code == 422
        assert client.post("/v1/generations", json={**BODY, "lyrics": "x" * 7000}, headers=AUTH).status_code == 422


def test_no_tokens_configured_fails_closed():
    with TestClient(create_app(Settings(app_tokens=[], deepinfra_api_key="dk"))) as client:
        assert client.get("/v1/providers", headers=AUTH).status_code == 503


def test_cancel_slow_job():
    import asyncio

    async def slow(request):
        await asyncio.sleep(30)
        return deepinfra_ok(request)

    with make_client(slow) as client:
        job_id = client.post("/v1/generations", json=BODY, headers=AUTH).json()["id"]
        info = client.post(f"/v1/generations/{job_id}/cancel", headers=AUTH).json()
        assert info["status"] == "cancelled"


def test_diagnostic_provider_is_labelled_not_ai():
    settings = Settings(app_tokens=[TOKEN], enable_diagnostic=True)
    with TestClient(create_app(settings)) as client:
        providers = client.get("/v1/providers", headers=AUTH).json()
        assert providers[0]["is_ai_music"] is False
        job = client.post("/v1/generations", json={**BODY, "audio_format": "wav", "duration_seconds": 10},
                          headers=AUTH).json()
        info = wait_done(client, job["id"])
        assert info["status"] == "succeeded"
