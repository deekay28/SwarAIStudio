# Architecture

```
Android app (Kotlin, Compose, Material 3)
 ├─ ui/          6 tabs: Home · Create · My Music · Studio · Voices · Settings, plus mini player
 ├─ data/        Room (projects, songs, presets, generation jobs) · DataStore settings · Keystore-encrypted secrets
 ├─ lyrics/      section parsing, model formatting, rhyme scheme, Devanagari romanisation (offline)
 ├─ engine/      MusicEngine interface
 │    ├─ BackendEngine          → SwarAI FastAPI backend (HTTPS + bearer token)
 │    ├─ DeepInfraDirectEngine  → api.deepinfra.com with the user's own key
 │    ├─ AceStepCompanionEngine → ACE-Step 1.5 REST API on the user's PC (LAN)
 │    └─ OnDeviceEngine         → explicitly unavailable, with the reason
 ├─ generation/  WorkManager worker: submit → poll → download → save; never auto-retries (no double billing);
 │               resumes by remote job id after process death where the engine allows it
 ├─ audio/       MediaCodec decode/AAC encode, WAV I/O, trim/fade/gain/normalise, waveform peaks (pure Kotlin)
 ├─ player/      Media3 ExoPlayer (local files only)
 ├─ video/       procedural cover art, Media3 Transformer lyric video (image + overlay + audio → H.264/AAC MP4)
 └─ export/      Storage Access Framework exports, project files, YouTube text, estimated SRT/LRC

backend/ (Python 3.12, FastAPI): optional, deploy anywhere
 ├─ providers/   deepinfra (ACE-Step 1.5 XL), fal (ACE-Step v1), acestep (self-hosted 1.5), diagnostic (test tone, not AI)
 ├─ jobs.py      in-memory jobs; audio in a temp dir, deleted on DELETE or after TTL
 └─ main.py      token auth, validation, daily audio budget, cost estimate, cancel

.github/workflows/
 ├─ android-apk.yml          unit tests, lint, debug APK artifact, emulator install/launch/offline tests
 ├─ android-release.yml      signed release APK from GitHub Secrets; optional DRAFT release
 ├─ backend-ci.yml           pytest + Docker image + container health check
 └─ provider-smoke-test.yml  manual, cost-confirmed real generation on a runner
```

## Execution modes

| Mode | Internet needed | Where lyrics go | Status |
|---|---|---|---|
| A. Online (DeepInfra direct or SwarAI server) | Yes | Provider (and your server) | Implemented |
| B1. On-device generation | No | Nowhere | **Not feasible**: see OFFLINE_REPORT.md |
| B2. Computer companion (ACE-Step 1.5 on your PC) | No (LAN only) | Your PC | Implemented |
| A2. Free cloud notebook (ACE-Step 1.5 on Colab via Cloudflare tunnel) | Yes | Your Colab VM | Implemented; labelled online |
| C. Offline studio (edit, play, organise, export, video) | No | Nowhere | Implemented and tested offline |

The app labels every engine with its mode and destination and never calls a remote engine "offline".

## Key decisions

- **Two online paths.** The backend is the secure default for keys. The in-app personal key exists because
  deploying a server from a phone is a hurdle; it is opt-in, encrypted, and clearly labelled.
- **No FFmpeg.** FFmpegKit was retired in 2025; platform MediaCodec + Media3 cover WAV, AAC and MP4 without native
  licensing issues. MP3 *encoding* is therefore not offered (MP3 originals can still be exported).
- **Estimated lyric timing.** None of the integrated providers return word timings to the app, so subtitle timing is
  computed from line lengths and labelled "estimated".
- **Honest editing.** Stem separation, section repaint and extension are shown as unavailable rather than faked.
