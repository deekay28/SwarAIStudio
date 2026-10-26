# Using SwarAI Studio from an Android phone (no laptop)

Everything below is done in your phone's browser, the GitHub app or website, and the Claude app.

## 1. Get the APK

Every push to `main` (and to `claude/**` working branches) builds a debug APK on GitHub Actions.

1. Open **https://github.com/deekay28/SwarAIStudio/actions** in Chrome (sign in to GitHub).
2. Tap the **Android APK** workflow, then the newest run with a green tick.
3. Scroll to **Artifacts** and tap **SwarAIStudio-debug-xxxxxxx**. GitHub downloads a `.zip`.
4. Open **Files** → **Downloads**, tap the zip, then tap `SwarAIStudio-debug-xxxxxxx.apk` inside it.
   (If your Files app can't open zips, the "Files by Google" app can.)
5. Android asks to allow installs from Chrome/Files: **Settings → Allow from this source**, go back, **Install**.
6. The app appears as **SwarAI Studio** (debug builds are named `com.swarai.studio.debug` and can sit next to a release build).

To build on demand: Actions → **Android APK** → **Run workflow** (this button appears once the workflow file is on `main`).

Each run also uploads `reports-…` (unit tests and lint) and `instrumented-reports-…` (the emulator install/launch/offline tests).

## 2. Choose a music engine (Settings tab)

| Option | What you need | Cost | Lyrics go to |
|---|---|---|---|
| **Free Colab notebook** ([FREE_COLAB.md](FREE_COLAB.md)) | A Google account | **Free** (limited daily GPU time) | Your Colab notebook (Google's servers) |
| **Free Kaggle notebook** ([FREE_KAGGLE.md](FREE_KAGGLE.md)) | A phone-verified Kaggle account | **Free** (~30 GPU hours/week) | Your Kaggle notebook (Google's servers) |
| **DeepInfra (your key)**: quickest paid option | A DeepInfra account and API key | ≈ $0.001 per second of audio (≈ $0.18 per 3-minute song), billed by DeepInfra | DeepInfra (USA) |
| **SwarAI server** (recommended for keeping keys off the phone) | Deploy `backend/` (see PROVIDER_SETUP.md) | Free hosting tier, plus provider fees | Your server, then the provider |
| **Your computer** (offline over Wi-Fi) | A PC with an NVIDIA GPU running ACE-Step 1.5 | No per-song fee | Your own PC only |
| On-device | Not possible today | n/a | n/a |

The app shows the estimated cost and where your lyrics will be sent **every time** before generating.

## 3. Make a song

**Create** tab:
1. Type a title and pick the language (हिन्दी, भोजपुरी, मैथिली, English…).
2. Paste lyrics. Tap **+ Verse / + Chorus** to add section tags. Drafts save automatically.
3. Pick a style (e.g. *Chhath Puja geet*), mood and intensity.
4. Pick a voice profile, or switch **Instrumental** on.
5. Optionally choose instruments, duration, BPM, number of candidates (1–4).
6. Tap **Generate song**, check the cost dialog, and confirm.
7. You can leave the screen; generation continues in the background. Songs appear in **My Music**.

## 4. Listen, edit, export

- **My Music**: play, rename, favourite, delete, import audio, export to Downloads/Drive. Works offline.
- **Studio**: trim, fade in/out, volume, normalise → **save as new version** (the original is kept).
  Export as WAV, AAC/M4A, or the original file (MP3 if the engine sent MP3).
- **YouTube**: title/description/tags (with an AI-content disclosure), estimated SRT/LRC subtitles,
  cover PNG (design generator or your own photo), and **16:9 or 9:16 lyric videos** rendered on the phone.

## 5. Keep developing from the phone

Open the Claude app → Claude Code → this repository, and describe the change. Claude edits, commits and pushes;
GitHub Actions rebuilds the APK; you download it as in step 1.
