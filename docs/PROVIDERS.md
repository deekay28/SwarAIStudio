# Music provider evaluation (October 2026)

Requirement: full songs from lyrics, realistic vocals plus accompaniment, Hindi/Bhojpuri suitability, variations,
long songs, commercial-use compatibility, and an API that actually exists.

## Chosen: ACE-Step 1.5 (open source, MIT)

- Source: https://github.com/ace-step/ACE-Step-1.5, MIT licence per the project README. Check the Hugging Face model card for the weights.
- Text-to-music with lyrics and vocals, **50+ lyric languages** (Hindi in Devanagari works; Bhojpuri/Maithili are
  sung with Hindi pronunciation because the model has no separate mode), 10 s to 10 min, batch of up to 8,
  BPM/key/time-signature control, and cover, repaint, extract and complete editing modes (some models only).
- Official **REST API server** (`acestep-api`: `/release_task`, `/query_result`, `/v1/audio`), used by the app's
  "Your computer" engine and the backend's self-hosted adapter.
- Hardware: CUDA/ROCm/MPS/XPU or CPU on a computer. The smallest config (2B turbo, LM off) targets ≤ 6 GB VRAM.

## Hosted inference that exists today

| Service | Model | API shape | Price (published) | Used as |
|---|---|---|---|---|
| **DeepInfra** `ACE-Step/acestep-v15-xl-sft` | ACE-Step 1.5 XL (4B DiT) with LM planning | Sync `POST https://api.deepinfra.com/v1/inference/ACE-Step/acestep-v15-xl-sft` (prompt, lyrics, duration 30–500 s, seed, instrumental, mp3/wav/flac) | **$0.001 / s of audio** | **Default**: in-app key or via backend |
| fal.ai `fal-ai/ace-step` | ACE-Step **v1** (3.5B, Apache-2.0) | Queue API with real cancel | ~$0.0002 / s | Backend alternative (weaker Hindi) |
| Replicate (`fishaudio/ace-step-1.5`, community) | ACE-Step 1.5 | Replicate predictions | per-second GPU time | Not integrated: community-maintained and schema unverified |

Notes:
- **Not verified from this environment.** The build sandbox's network policy blocks deepinfra.com, fal.ai,
  replicate.com and huggingface.co, so the DeepInfra request/response format comes from its published API page.
  The client accepts every documented audio encoding (data URI, base64, URL), and the **Provider smoke test**
  workflow performs the real end-to-end check on a GitHub runner once you add a key.
- acemusic.ai offers free generation in a web UI, but no documented public API, so it is not integrated.
- Suno and Udio have no official public API; unofficial wrappers violate their terms, so they were excluded.

## Commercial use

- ACE-Step 1.5 is MIT-licensed; the project disclaims responsibility for outputs that resemble copyrighted works.
- Hosted use is also governed by the provider's terms (DeepInfra / fal.ai). Read them before monetising.
- The app never imitates named singers; voice profiles are descriptive (register, timbre, delivery).
- YouTube: disclose realistic synthetic content. Monetisation and Content ID outcomes are not guaranteed.

## Swapping providers without rebuilding the app

The app talks to the SwarAI backend through a provider-neutral API (`/v1/providers`, `/v1/generations`).
Add an adapter in `backend/swarai_backend/providers/` (implement `Provider.generate`) and set its env var;
the app discovers it via `/v1/providers` and shows its name, capabilities and pricing automatically.
