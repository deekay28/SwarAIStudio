# Offline capability and compatibility report

## Summary

| Capability | Offline? | Evidence |
|---|---|---|
| Open/edit/save lyrics (Devanagari), autosave | ✅ | `LyricsPersistenceTest` (emulator, airplane mode) |
| Projects, presets, library: rename, favourite, delete | ✅ | `DatabaseTest`, `LaunchTest` (airplane mode) |
| Play saved songs | ✅ | `PlaybackAndVideoTest.playsLocalFileWithoutNetwork` (airplane mode) |
| Trim, fade, volume, normalise, WAV/AAC conversion | ✅ | `AudioPipelineTest` (real MediaCodec, airplane mode) + JVM `AudioEffectsTest` |
| Lyric video (16:9 / 9:16) with visualizer | ✅ | `PlaybackAndVideoTest.rendersLyricVideoOnDevice` (airplane mode) |
| Export to storage (SAF), project files, SRT/LRC, cover PNG | ✅ | Local-only code paths; JVM `SubtitlesTest` |
| Rhyme & pronunciation help | ✅ | JVM `LyricsToolsTest` (rule-based, no network) |
| **AI song generation on the phone** | ❌ | Not feasible today (below) |
| AI generation via **your computer** on Wi-Fi without internet | ✅ when a PC runs ACE-Step | Engine marked "works without internet"; needs your hardware to verify |

The CI emulator job (`android-apk.yml` → *emulator*) switches the emulator to **airplane mode with Wi-Fi and data off**,
then runs the whole instrumented suite with `expectOffline=true`. `OfflineEvidenceTest` fails if the device still has
internet, so a green run is evidence that every listed test passed without a connection. Reports are uploaded as
`instrumented-reports-<sha>` (including `offline-evidence.txt`).

## Why on-device generation is not offered

- ACE-Step 1.5's smallest supported configuration (2B "turbo" DiT, LM disabled, INT8, CPU offload) targets GPUs with
  ≤ 6 GB VRAM via PyTorch with CUDA/ROCm/MPS/XPU, or a desktop CPU. No Android runtime (LiteRT/NNAPI/Vulkan/ExecuTorch)
  port exists, and the weights (several GB) exceed typical app limits.
- A phone CPU would take far longer than a desktop CPU and would hit thermal limits.
- The app therefore lists **On-device generation** as unavailable with this explanation instead of hiding or faking it.

Re-evaluate if an official mobile export (e.g. ExecuTorch or GGML on Android) of ACE-Step appears;
`OnDeviceEngine` is the slot for it.

## Computer companion compatibility

| Platform | Backend | Notes |
|---|---|---|
| Windows + NVIDIA | CUDA | Portable package available; recommended |
| Linux + NVIDIA/AMD | CUDA / ROCm | |
| macOS Apple Silicon | MLX / MPS | Slower than a recent NVIDIA GPU |
| CPU only | PyTorch CPU | Works but slow |

The phone reaches the PC at `http://<LAN-IP>:8001`. Plain HTTP is allowed **only** for private-network addresses.
