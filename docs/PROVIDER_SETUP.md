# Provider setup

SwarAI Studio never ships with a provider key. Pick **one** of the options below.

> **Want it free?** Use the Google Colab notebook (**[FREE_COLAB.md](FREE_COLAB.md)**) or, when Colab has no GPU free, the Kaggle notebook (**[FREE_KAGGLE.md](FREE_KAGGLE.md)**). No card, no PC needed.

## Option A: DeepInfra key in the app (fastest, about 5 minutes)

1. On your phone, sign up at **deepinfra.com** and add a payment method. **Set a monthly spending limit** in billing.
2. Create an API key (Dashboard → API keys).
3. In SwarAI Studio: **Settings → DeepInfra — your own key**, paste the key, **Save**, then **Test (free)**.
4. Select the **ACE-Step 1.5 XL on DeepInfra (your key)** engine.

The key is encrypted with the Android Keystore and sent only to `api.deepinfra.com`.
Pricing at the time of writing: **$0.001 per second of generated audio** (check DeepInfra's model page; prices change).

## Option B: your own SwarAI server (keeps keys off the phone)

The backend (`backend/`, Python FastAPI) holds provider keys as server-side secrets, authenticates the app with a token,
validates requests, enforces a **daily audio budget**, supports cancellation, and deletes audio after download or 1 hour.
It needs no GPU: a free tier is enough.

### Render (no laptop, from the phone browser)
1. Sign in at **render.com** with GitHub.
2. **New → Blueprint** → choose `deekay28/SwarAIStudio`. Render reads `render.yaml`.
3. When asked, paste `DEEPINFRA_API_KEY`. Render generates `SWARAI_APP_TOKENS`.
4. After deploy, open the service → **Environment** and copy the `SWARAI_APP_TOKENS` value.
5. In the app: **Settings → SwarAI server**, enter `https://<your-service>.onrender.com` and the token, **Save**, **Test**, then select the engine.

The free Render tier sleeps after inactivity; the first request after a nap takes ~1 minute.
Run a single instance: jobs are kept in memory.

### Other hosts
Any container host works (Google Cloud Run with `--max-instances=1`, Fly.io, Hugging Face Spaces with Docker).
Environment variables: see `backend/.env.example`.

### Verify end-to-end from GitHub (optional, paid)
Add repository secret `DEEPINFRA_API_KEY` (Settings → Secrets and variables → Actions), then run
**Actions → Provider smoke test** with `confirm_cost` = `I accept the cost`. It starts the backend on the runner,
generates one 30-second Bhojpuri devotional song (~$0.03), and uploads the MP3 plus logs as an artifact.

## Option C: ACE-Step 1.5 on your own computer (offline over Wi-Fi)

Requires a PC with an NVIDIA GPU (≈ 6 GB+ VRAM recommended; see ACE-Step's GPU table). On the PC:
```
git clone https://github.com/ace-step/ACE-Step-1.5.git
cd ACE-Step-1.5
uv sync
ACESTEP_API_HOST=0.0.0.0 ACESTEP_API_KEY=choose-a-password uv run acestep-api
```
(Windows: the portable package and `start_api_server.bat`; set `ACESTEP_API_HOST=0.0.0.0` in `.env`.)
In the app: **Settings → Your computer**, address `http://<PC-IP>:8001`, key = the password, **Test**, then select it.
Works with the router's Wi-Fi even if the internet is down. Tailscale (100.x addresses) also works away from home.

## Provider comparison

See [PROVIDERS.md](PROVIDERS.md) for the evaluation, licensing and why these were chosen.
