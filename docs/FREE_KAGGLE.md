# Free music generation with Kaggle

Kaggle gives free GPU time (about **30 hours per week**) that is separate from Google Colab's. Use this when Colab says
*"No backend with GPU available"*. Everything works from your phone's browser.

## One-time setup (5 minutes)
1. Create a free account at **kaggle.com** (you can sign in with Google).
2. **Verify your phone number:** Kaggle → your profile picture → **Settings** → **Phone verification**.
   Without this, Kaggle won't let notebooks use a GPU or the internet.

## Step 1: open the notebook
Open this link in Chrome (sign in to Kaggle if asked):

**https://www.kaggle.com/kernels/welcome?src=https://github.com/deekay28/SwarAIStudio/blob/main/kaggle/SwarAI_ACE-Step_Kaggle.ipynb**

Kaggle makes your own copy of the notebook. (If the link doesn't work: Kaggle → **Create → New Notebook** →
**File → Import Notebook** → **GitHub** tab → paste `deekay28/SwarAIStudio` → choose `kaggle/SwarAI_ACE-Step_Kaggle.ipynb`.)

Tip: in Chrome's ⋮ menu tick **Desktop site**; Kaggle's editor is easier to use that way on a phone.

## Step 2: settings (every new copy)
In the notebook's right-hand panel (**Session options** / **Settings**; on a narrow screen open it with the arrow or ⋮):
- **Accelerator → GPU T4 x2.** Not **P100**: it's too old for the model's PyTorch build, and the notebook will stop with a message.
- **Internet → On.**

## Step 3: run it
1. Tap ▶️ on the code cell (or **Run all**). The first start takes **10–20 minutes**.
2. When it's ready you'll see:
   ```
   ✅ READY — paste these into SwarAI Studio
      Address : https://something-random.trycloudflare.com
      Password: Xy7…
   ```
3. In SwarAI Studio go to **Settings → ACE-Step server (your PC or free Colab/Kaggle notebook)**.
   Paste both, tap **Save**, then **Test**, and select **ACE-Step 1.5 on your cloud notebook** under **Music engine**.
4. Keep the Kaggle tab open while you make songs. When you're done, **stop the session** (power icon / **Stop session**)
   so you don't use up your weekly GPU hours.

## Good to know
- **Free** within Kaggle's weekly GPU quota (your remaining hours are shown on your Kaggle profile). Sessions stop when
  idle or after several hours. Each start gives a **new address** to paste into the app.
- **It's online, not offline:** your lyrics go to your Kaggle machine (Google's servers) through a Cloudflare tunnel,
  protected by the password. Kaggle may restrict some uses; see Kaggle's terms.
- The notebook keeps its data in temporary storage, so a brand-new session downloads the model again.

## If something goes wrong
| Message | Fix |
|---|---|
| "No GPU found" / "GPU is too old" | Accelerator → **GPU T4 x2**, then run again |
| "No internet access" | Internet → **On** (needs phone verification), then run again |
| App can't connect / HTTP 530 | The session stopped. Run the cell again and paste the new address |
| Out of memory | Change `LM_MODEL` at the top of the cell to `"acestep-5Hz-lm-0.6B"` or `"none"` |

Both notebooks (Colab and Kaggle) are generated from the same code, [`colab/server_cell.py`](../colab/server_cell.py),
and the **Colab & Kaggle notebook check** workflow tests both.
