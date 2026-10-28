import base64
import json

import httpx
import pytest

from swarai_backend.providers.acestep import SelfHostedAceStep, failure_message, parse_result_field
from swarai_backend.providers.base import ProviderError, decode_audio_field, sniff_format
from swarai_backend.providers.deepinfra import DeepInfraAceStep
from swarai_backend.providers.diagnostic import tone_wav
from swarai_backend.providers.fal import FalAceStep
from swarai_backend.schemas import GenerationRequest

FAKE_MP3 = b"ID3" + b"\x00" * 4000
HINDI = "[verse]\nछठ के घाट पे दियरा जलाईं\n[chorus]\nजय हो छठी मईया"


def req(**kw):
    base = dict(caption="bhojpuri folk, devotional, female vocal, dholak", lyrics=HINDI, duration_seconds=60)
    base.update(kw)
    return GenerationRequest(**base)


@pytest.mark.asyncio
async def test_deepinfra_payload_and_data_uri_response():
    seen = {}

    def handler(request: httpx.Request):
        seen["auth"] = request.headers["authorization"]
        seen["url"] = str(request.url)
        seen["body"] = json.loads(request.content)
        audio = "data:audio/mp3;base64," + base64.b64encode(FAKE_MP3).decode()
        return httpx.Response(200, json={"audio": audio, "output_format": "mp3", "seed": 7,
                                         "duration_seconds": 60.0, "inference_status": {"status": "succeeded"}})

    provider = DeepInfraAceStep("k-123", "ACE-Step/acestep-v15-xl-sft", 0.001)
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        result = await provider.generate(req(bpm=96), 7, client)
    assert seen["auth"] == "Bearer k-123"
    assert seen["url"].endswith("/v1/inference/ACE-Step/acestep-v15-xl-sft")
    assert seen["body"]["lyrics"] == HINDI  # Devanagari preserved byte-for-byte
    assert seen["body"]["seed"] == 7 and seen["body"]["duration"] == 60
    assert "96 BPM" in seen["body"]["prompt"]
    assert result.audio == FAKE_MP3 and result.format == "mp3" and result.seed == 7


@pytest.mark.asyncio
async def test_deepinfra_clamps_duration_and_drops_lyrics_for_instrumental():
    provider = DeepInfraAceStep("k", "m", 0.001)
    payload = provider.build_payload(req(duration_seconds=10, instrumental=True), None)
    assert payload["duration"] == 30 and payload["lyrics"] == "" and payload["instrumental"] is True


@pytest.mark.asyncio
async def test_deepinfra_auth_error_is_user_safe():
    provider = DeepInfraAceStep("secret-key", "m", 0.001)
    transport = httpx.MockTransport(lambda r: httpx.Response(401, json={"detail": "bad key"}))
    async with httpx.AsyncClient(transport=transport) as client:
        with pytest.raises(ProviderError) as err:
            await provider.generate(req(), None, client)
    assert "credentials" in str(err.value) and "secret-key" not in str(err.value)


@pytest.mark.asyncio
async def test_fal_queue_flow():
    calls = []

    def handler(request: httpx.Request):
        calls.append((request.method, request.url.path))
        if request.method == "POST":
            assert request.headers["authorization"] == "Key fk"
            return httpx.Response(200, json={"request_id": "r1", "status_url": "https://queue.fal.run/x/requests/r1/status",
                                             "response_url": "https://queue.fal.run/x/requests/r1",
                                             "cancel_url": "https://queue.fal.run/x/requests/r1/cancel"})
        if request.url.path.endswith("/status"):
            state = "IN_PROGRESS" if sum(1 for c in calls if c[1].endswith("/status")) < 2 else "COMPLETED"
            return httpx.Response(200, json={"status": state})
        if request.url.path.endswith("/r1"):
            return httpx.Response(200, json={"audio": {"url": "https://v3.fal.media/files/a.wav"}, "seed": 3})
        if request.url.host == "v3.fal.media":
            return httpx.Response(200, content=b"RIFF\x00\x00\x00\x00WAVE" + b"\x00" * 2000)
        return httpx.Response(404)

    provider = FalAceStep("fk", "fal-ai/ace-step", 0.0002, poll_interval=0)
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        result = await provider.generate(req(), 3, client)
    assert result.format == "wav" and result.seed == 3


@pytest.mark.asyncio
async def test_selfhosted_acestep_flow():
    def handler(request: httpx.Request):
        path = request.url.path
        if path == "/release_task":
            body = json.loads(request.content)
            assert body["vocal_language"] == "hi" and body["lyrics"] == HINDI and body["thinking"] is True
            return httpx.Response(200, json={"data": {"task_id": "t1", "status": "queued"}, "code": 200})
        if path == "/query_result":
            result = json.dumps([{"file": "/v1/audio?path=%2Ftmp%2Fa.mp3", "status": 1, "seed_value": "42,43",
                                  "metas": {"duration": 60}}])
            return httpx.Response(200, json={"data": [{"task_id": "t1", "status": 1, "result": result}], "code": 200})
        if path == "/v1/audio":
            assert request.url.params["path"] == "/tmp/a.mp3"
            return httpx.Response(200, content=FAKE_MP3)
        return httpx.Response(404)

    provider = SelfHostedAceStep("http://192.168.1.20:8001", poll_interval=0)
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        result = await provider.generate(req(), None, client)
    assert result.seed == 42 and result.format == "mp3" and result.duration_seconds == 60


@pytest.mark.asyncio
async def test_decode_audio_rejects_garbage():
    async with httpx.AsyncClient() as client:
        with pytest.raises(ProviderError):
            await decode_audio_field("not base64 !!", client)
        with pytest.raises(ProviderError):
            await decode_audio_field(None, client)


def test_sniff_and_parse_helpers():
    assert sniff_format(tone_wav(1, 440), "mp3") == "wav"
    assert sniff_format(b"fLaC....", "mp3") == "flac"
    assert parse_result_field('{"file": "x"}') == [{"file": "x"}]
    assert parse_result_field("garbage") == []


def test_request_validation_normalises_text():
    r = req(lyrics="क़\r\nline\x07")  # decomposed nukta + CRLF + BEL
    assert r.lyrics == "क़\nline"
    with pytest.raises(ValueError):
        req(key_scale="C; rm -rf")
    with pytest.raises(ValueError):
        req(duration_seconds=5)


@pytest.mark.asyncio
async def test_selfhosted_disables_thinking_without_lm():
    sent = {}

    def handler(request: httpx.Request):
        if request.url.path == "/health":
            return httpx.Response(200, json={"data": {"llm_initialized": False}})
        if request.url.path == "/release_task":
            sent.update(json.loads(request.content))
            return httpx.Response(200, json={"data": {"task_id": "t9"}})
        if request.url.path == "/query_result":
            return httpx.Response(200, json={"data": [{"task_id": "t9", "status": 1, "result": json.dumps([{"file": "/v1/audio?path=a"}])}]})
        return httpx.Response(200, content=FAKE_MP3)

    provider = SelfHostedAceStep("https://x.trycloudflare.com", "pw", poll_interval=0)
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        await provider.generate(req(), None, client)
    assert sent["thinking"] is False


@pytest.mark.asyncio
async def test_selfhosted_retries_tunnel_hiccup():
    polls = {"n": 0}

    def handler(request: httpx.Request):
        if request.url.path == "/release_task":
            return httpx.Response(200, json={"data": {"task_id": "t5"}})
        if request.url.path == "/query_result":
            polls["n"] += 1
            if polls["n"] <= 2:
                return httpx.Response(530)
            return httpx.Response(200, json={"data": [{"task_id": "t5", "status": 1, "result": json.dumps([{"file": "/v1/audio?path=a"}])}]})
        if request.url.path == "/health":
            return httpx.Response(200, json={"data": {"llm_initialized": True}})
        return httpx.Response(200, content=FAKE_MP3)

    provider = SelfHostedAceStep("https://x.trycloudflare.com", "pw", poll_interval=0)
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        result = await provider.generate(req(), None, client)
    assert polls["n"] == 3 and result.audio == FAKE_MP3


def test_failure_message_extracts_cause():
    tb = "Traceback (most recent call last):\n  File \"x\"\ntorch.OutOfMemoryError: CUDA out of memory."
    msg = failure_message({"status": 2, "result": json.dumps([{"status": 2, "error": tb}])})
    assert "torch.OutOfMemoryError: CUDA out of memory." in msg and "shorter song" in msg
