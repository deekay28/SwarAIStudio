# Free music generation with Google Colab

Run the ACE-Step 1.5 model for free on Google's Colab GPU and connect SwarAI Studio to it. Everything is done
on your phone. You need a Google account.

## Cost and limits (read once)
- **Free.** No card needed. Google gives free Colab users a limited amount of GPU time (typically a few hours a day;
  not guaranteed). When it runs out, Colab says it can't connect to a GPU. Try again later.
- **It's online, not offline.** Your lyrics go to your Colab machine (Google's servers) through a free Cloudflare
  tunnel. The server is protected by a random password only you see.
- The session stops if you close the tab, stay idle for a long time, or after several hours. Then you start it again,
  and you get a **new address**.
- Google may restrict some uses of free Colab; see the [Colab FAQ](https://research.google.com/colaboratory/faq.html).

## Step 1: open the notebook
Open this link in Chrome on your phone (sign in to Google if asked):

**https://colab.research.google.com/github/deekay28/SwarAIStudio/blob/main/colab/SwarAI_ACE-Step_Colab.ipynb**

(If it says "not found", the notebook isn't on `main` yet. In Colab use **File → Open notebook → GitHub**, search
`deekay28/SwarAIStudio`, choose the branch `claude/swarai-studio-android-ggllj6`, and tap `colab/SwarAI_ACE-Step_Colab.ipynb`.)

Tip: in Chrome's ⋮ menu tick **Desktop site** if the Colab buttons are hard to tap.

## Step 2: run it
1. Tap the **▶️** button on the big cell. If Colab warns "This notebook was not authored by Google", tap **Run anyway**.
2. Wait. The first start takes **10–20 minutes** (installing ACE-Step, downloading the ~6 GB model). Progress lines appear.
3. When it's ready you'll see:
   ```
   ✅ READY — paste these into SwarAI Studio
      Address : https://something-random.trycloudflare.com
      Password: Xy7…
   ```
   Long-press to copy each one.

If you see **"No GPU found"**: menu **Runtime → Change runtime type → T4 GPU → Save**, then tap ▶️ again.

## Step 3: connect the app
1. SwarAI Studio → **Settings** → card **ACE-Step server (your PC or free Colab notebook)**.
2. Paste the **Address** and the **Password**, tap **Save**, then **Test**. It should say *Connected to ACE-Step server*.
3. At the top of Settings, under **Music engine**, select **ACE-Step 1.5 on your cloud notebook**.
4. Go to **Create**, write your song, tap **Generate**. The dialog will say "No per-song charge".

A 2-minute song usually takes 1–3 minutes on the free T4 GPU. Keep the Colab tab open (switch apps, don't close it).

## Next time
Open the same link, tap ▶️ (faster now if the session is still alive; otherwise ~10–20 minutes again),
and paste the **new address** into Settings. The password stays the same within a session.

## If something goes wrong
| Problem | Fix |
|---|---|
| "No backend with GPU available" / "You cannot currently connect to a GPU" | Free GPU limit reached. Tap **Cancel**, try later, or use the [Kaggle notebook](FREE_KAGGLE.md) |
| App: "failed", "HTTP 530" or "can't connect" | The notebook stopped. Run ▶️ again and paste the new address |
| "ran out of memory (RAM)" / server stopped with no error | Free Colab has little RAM. The notebook retries by itself with a smaller song planner (1.7B → 0.6B → none); if it still fails, use the [Kaggle notebook](FREE_KAGGLE.md) |
| Generation fails only for long songs | Keep songs ≤ 4 minutes on the free GPU |

## What the notebook does (for the curious)
It clones the official ACE-Step 1.5 repository, installs it with `uv`, starts the official `acestep-api` server
(turbo 2B model, PyTorch LM backend, FlashAttention off for the T4) with a random password, and opens a
Cloudflare quick tunnel to it. The code is in [`colab/server_cell.py`](../colab/server_cell.py), and the
**Colab & Kaggle notebook check** workflow runs that same code on a GitHub runner to test it.
