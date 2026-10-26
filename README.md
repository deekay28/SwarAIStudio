# SwarAI Studio (स्वर AI)

An Android studio that turns lyrics (Hindi, Bhojpuri, Maithili, English and more) into complete songs with vocals
and accompaniment, using the open-source **ACE-Step 1.5** model through the engine you choose. You can then edit,
make lyric videos, and export for YouTube.

**Paste lyrics → Choose style → Choose voice → Generate → Listen → Save → Export**

- 📱 **Install from your phone:** [docs/PHONE_GUIDE.md](docs/PHONE_GUIDE.md)
- 🔑 **Set up a music engine:** [docs/PROVIDER_SETUP.md](docs/PROVIDER_SETUP.md) (DeepInfra key, your own server, or your PC)
- 🧭 Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · Providers & licensing: [docs/PROVIDERS.md](docs/PROVIDERS.md)
- 📴 Offline report: [docs/OFFLINE_REPORT.md](docs/OFFLINE_REPORT.md) · Privacy: [docs/PRIVACY.md](docs/PRIVACY.md)
- 🔏 Signed releases: [docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md)

## What works

| Area | Features |
|---|---|
| Lyrics | Unicode/Devanagari editor, section tags (intro/verse/chorus/bridge/outro, also mukhda/antara), autosave, import/export .txt, original preserved, rhyme scheme, romanised pronunciation guide |
| Style | 27 genres incl. Bhojpuri folk & devotional, Chhath Puja, Maithili, bhajan, ghazal, qawwali, Sufi, Hindi film, wedding, children's, pop/rock/rap/EDM/lo-fi, orchestral, meditation · moods · intensity · instruments · arrangement · BPM · key · duration · 1–4 candidates · advanced prompt · presets |
| Voices | 15 original voice profiles (female, male, androgynous, youthful, duet, group) with real model-generated previews on request |
| Engines | ACE-Step 1.5 XL on DeepInfra (own key) · SwarAI backend (keys server-side) · ACE-Step 1.5 on your PC over Wi-Fi · on-device shown as unavailable, with the reason |
| Library | Offline playback, rename, favourites, search/filter, import audio, delete, export, project files |
| Studio | Waveform, trim, fades, volume, normalise, save as new version, WAV and AAC/M4A export |
| YouTube | Title/description/tags with AI disclosure, SRT/LRC (estimated timing), cover PNG (design generator or own image), 16:9 and 9:16 lyric videos with visualizer |

Not available (and labelled so in the app): on-device AI generation, stem separation, section regeneration,
song extension, MP3 encoding of edits.

## Repository layout

```
app/       Android app (Kotlin, Jetpack Compose, Material 3, Room, Media3, WorkManager)
backend/   Optional FastAPI backend that keeps provider keys server-side
docs/      Guides and reports
.github/   CI: APK build + emulator tests, signed release, backend CI, provider smoke test
```

## Costs

- GitHub Actions: free for public repositories.
- DeepInfra ACE-Step 1.5 XL: about $0.001 per second of audio (≈ $0.18 per 3-minute song), charged to your DeepInfra account.
- Backend hosting: fits free tiers (no GPU needed). The backend enforces a daily audio budget (default 30 minutes).
- Your own PC: electricity only.

## Licences

App and backend code: no licence file has been chosen yet (all rights reserved by the owner until one is added). ACE-Step 1.5: MIT. AI-generated music may coincidentally resemble
existing works; YouTube monetisation and copyright clearance are not guaranteed.
