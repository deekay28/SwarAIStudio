"""Builds the Colab and Kaggle notebooks from server_cell.py (the single source of truth).
Run: python colab/build_notebook.py"""
import json
import pathlib

here = pathlib.Path(__file__).parent
root = here.parent
code = (here / "server_cell.py").read_text()

COMMON_NOTES = """- **This is online, not offline:** your lyrics go to this notebook machine (Google's servers) through a free Cloudflare tunnel. Nobody else can use it without the password.
- Each time you start the notebook you get a **new address**. Paste it into the app again.
- Songs made with ACE-Step: the model is MIT-licensed; you're responsible for your lyrics and how you publish.
"""

colab_intro = """# 🎵 SwarAI Studio — free ACE-Step 1.5 server on Google Colab

This notebook runs the open-source **ACE-Step 1.5** music model (MIT licence) on Google Colab's **free T4 GPU**
and gives you an address + password to paste into the SwarAI Studio app.

**How to use (on your phone):**
1. Make sure the runtime is a GPU: menu **⋮ → Change runtime type → T4 GPU** (usually already set).
2. Tap ▶️ on the cell below and wait. First start: ~10–20 minutes.
3. Copy the **Address** and **Password** it prints into SwarAI Studio → **Settings → ACE-Step server**, tap **Save**, **Test**, select it.
4. Keep this tab open while you make songs.

**Good to know**
- Cost: **free**, within Google's free Colab limits (a few hours of GPU per day, not guaranteed; sessions stop when idle or after several hours). If Colab says *"No backend with GPU available"*, tap **Cancel** and try later, or use the **Kaggle** notebook instead.
- Google may restrict some uses of free Colab; check the [Colab FAQ](https://research.google.com/colaboratory/faq.html).
""" + COMMON_NOTES

colab_trouble = """### Troubleshooting
- **"No backend with GPU available"** / **"You cannot currently connect to a GPU"**: the free GPU limit is reached. Tap **Cancel** (CPU is far too slow), try again later, or use the Kaggle notebook.
- **"No GPU found"**: Runtime → Change runtime type → **T4 GPU** → Save → run again.
- **App says it can't connect**: the notebook was stopped or restarted; run the cell again and paste the *new* address.
- **Out of memory**: choose `acestep-5Hz-lm-0.6B` (or `none`) in the cell's form and run again, or ask for shorter songs.
- Logs: `/content/acestep-server.log` and `/content/cloudflared.log` (folder icon on the left).
"""

kaggle_intro = """# 🎵 SwarAI Studio — free ACE-Step 1.5 server on Kaggle

This notebook runs the open-source **ACE-Step 1.5** music model (MIT licence) on Kaggle's **free T4 GPU**
and gives you an address + password to paste into the SwarAI Studio app. Kaggle's free GPU time
(about 30 hours a week) is separate from Google Colab's.

**One-time setup:** your Kaggle account must be **phone-verified** (Kaggle → Settings → Phone verification);
otherwise Kaggle won't allow GPU or internet.

**How to use (on your phone):**
1. Open the notebook's settings: on a phone tap **⋮** (or the panel arrow) → **Settings** / **Session options**:
   - **Accelerator → GPU T4 x2** (do **not** pick P100, it's too old for this model)
   - **Internet → On**
2. Tap ▶️ on the code cell below (or **Run all**) and wait. First start: ~10–20 minutes.
3. Copy the **Address** and **Password** it prints into SwarAI Studio → **Settings → ACE-Step server**, tap **Save**, **Test**, select it.
4. Keep this tab open while you make songs. Stop the session when done (it saves your weekly GPU hours).

**Good to know**
- Cost: **free**, within Kaggle's weekly GPU quota (shown on your Kaggle profile). Sessions stop when idle or after several hours.
- Kaggle may restrict some uses; see Kaggle's terms.
""" + COMMON_NOTES

kaggle_trouble = """### Troubleshooting
- **"No GPU found"** or **"GPU is too old"**: Settings → Accelerator → **GPU T4 x2** → run again.
- **"No internet access"**: Settings → **Internet On** (requires phone verification), then run again.
- **App says it can't connect**: the session stopped; run the cell again and paste the *new* address.
- **Out of memory**: set `LM_MODEL = "acestep-5Hz-lm-0.6B"` (or `"none"`) at the top of the cell and run again.
- Logs: `/kaggle/temp/acestep-server.log` and `/kaggle/temp/cloudflared.log` (or `/tmp/swarai/…`).
"""


def lines(text):
    return text.splitlines(keepends=True)


def notebook(intro, trouble, metadata):
    return {
        "nbformat": 4,
        "nbformat_minor": 0,
        "metadata": metadata,
        "cells": [
            {"cell_type": "markdown", "metadata": {"id": "intro"}, "source": lines(intro)},
            {"cell_type": "code", "metadata": {"id": "start", "cellView": "form"}, "execution_count": None,
             "outputs": [], "source": lines(code)},
            {"cell_type": "markdown", "metadata": {"id": "trouble"}, "source": lines(trouble)},
        ],
    }


targets = {
    here / "SwarAI_ACE-Step_Colab.ipynb": notebook(colab_intro, colab_trouble, {
        "colab": {"provenance": [], "gpuType": "T4", "name": "SwarAI_ACE-Step_Colab.ipynb"},
        "accelerator": "GPU",
        "kernelspec": {"name": "python3", "display_name": "Python 3"},
        "language_info": {"name": "python"},
    }),
    root / "kaggle" / "SwarAI_ACE-Step_Kaggle.ipynb": notebook(kaggle_intro, kaggle_trouble, {
        "kaggle": {"accelerator": "nvidiaTeslaT4", "isInternetEnabled": True, "isGpuEnabled": True,
                   "language": "python", "sourceType": "notebook", "dataSources": []},
        "kernelspec": {"name": "python3", "display_name": "Python 3", "language": "python"},
        "language_info": {"name": "python"},
    }),
}
for path, nb in targets.items():
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(nb, indent=1, ensure_ascii=False) + "\n")
    print("wrote", path)
