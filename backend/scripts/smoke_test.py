"""End-to-end check against a running backend: generate one song and save the audio.

Usage: SWARAI_URL=https://... SWARAI_TOKEN=... python scripts/smoke_test.py [provider_id] [out_dir]
This makes a REAL (possibly paid) request when pointed at a paid provider.
"""

from __future__ import annotations

import json
import os
import sys
import time
from pathlib import Path

import httpx

LYRICS = """[verse]
उगते सूरज के लाली, छठी मईया के घाट
[chorus]
जय हो छठी मईया, सुन ल हमरो पुकार
"""


def main() -> int:
    url = os.environ["SWARAI_URL"].rstrip("/")
    headers = {"Authorization": f"Bearer {os.environ['SWARAI_TOKEN']}"}
    provider = sys.argv[1] if len(sys.argv) > 1 else None
    out_dir = Path(sys.argv[2] if len(sys.argv) > 2 else ".")
    out_dir.mkdir(parents=True, exist_ok=True)
    body = {
        "provider": provider,
        "caption": "Bhojpuri devotional folk, Chhath Puja, female vocal, dholak, harmonium, shehnai, warm",
        "lyrics": LYRICS,
        "language": "hi",
        "duration_seconds": int(os.environ.get("SMOKE_DURATION", "30")),
        "variations": 1,
        "audio_format": os.environ.get("SMOKE_FORMAT", "mp3"),
    }
    with httpx.Client(base_url=url, headers=headers, timeout=120) as client:
        print("health:", client.get("/health").json())
        print("providers:", json.dumps(client.get("/v1/providers").json(), indent=1, ensure_ascii=False))
        print("estimate:", client.post("/v1/estimate", json=body).json())
        job = client.post("/v1/generations", json=body)
        job.raise_for_status()
        job_id = job.json()["id"]
        started = time.time()
        while True:
            info = client.get(f"/v1/generations/{job_id}").json()
            if info["status"] not in ("queued", "running"):
                break
            if time.time() - started > 900:
                print("timed out"); return 1
            time.sleep(3)
        print("final:", json.dumps(info, indent=1, ensure_ascii=False))
        if info["status"] != "succeeded":
            return 1
        out = info["outputs"][0]
        audio = client.get(out["audio_path"])
        audio.raise_for_status()
        path = out_dir / f"smoke-{info['provider']}.{out['format']}"
        path.write_bytes(audio.content)
        print(f"saved {path} ({len(audio.content)} bytes) in {time.time() - started:.1f}s")
        client.delete(f"/v1/generations/{job_id}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
