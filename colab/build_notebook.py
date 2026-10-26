"""Builds SwarAI_ACE-Step_Colab.ipynb from server_cell.py (the single source of truth). Run: python colab/build_notebook.py"""
import json
import pathlib

here = pathlib.Path(__file__).parent
code = (here / "server_cell.py").read_text()

intro = """# 🎵 SwarAI Studio — free ACE-Step 1.5 server on Google Colab

This notebook runs the open-source **ACE-Step 1.5** music model (MIT licence) on Google Colab's **free T4 GPU**
and gives you an address + password to paste into the SwarAI Studio app.

**How to use (on your phone):**
1. Make sure the runtime is a GPU: menu **⋮ → Change runtime type → T4 GPU** (usually already set).
2. Tap ▶️ on the cell below and wait. First start: ~10–20 minutes.
3. Copy the **Address** and **Password** it prints into SwarAI Studio → **Settings → ACE-Step server**, tap **Save**, **Test**, select it.
4. Keep this tab open while you make songs.

**Good to know**
- Cost: **free**, within Google's free Colab limits (a few hours of GPU per day, not guaranteed; sessions stop when idle or after several hours).
- **This is online, not offline:** your lyrics go to this Colab machine (Google's servers) through a free Cloudflare tunnel. Nobody else can use it without the password.
- Each time you start the notebook you get a **new address**. Paste it into the app again.
- Google may restrict some uses of free Colab; check the [Colab FAQ](https://research.google.com/colaboratory/faq.html). If your session is limited, use your own PC or DeepInfra instead.
- Songs made with ACE-Step: the model is MIT-licensed; you're responsible for your lyrics and how you publish.
"""

trouble = """### Troubleshooting
- **"No GPU found"**: Runtime → Change runtime type → **T4 GPU** → Save → run again.
- **"You cannot currently connect to a GPU"**: the free daily GPU limit is reached; try again later.
- **App says it can't connect**: the notebook was stopped or restarted, so run the cell again and paste the *new* address.
- **Out of memory**: choose `acestep-5Hz-lm-0.6B` (or `none`) in the cell's form and run again, or ask for shorter songs.
- Logs: `/content/acestep-server.log` and `/content/cloudflared.log` (folder icon on the left).
"""

def lines(text):
    parts = text.splitlines(keepends=True)
    return parts

notebook = {
    "nbformat": 4,
    "nbformat_minor": 0,
    "metadata": {
        "colab": {"provenance": [], "gpuType": "T4", "name": "SwarAI_ACE-Step_Colab.ipynb"},
        "accelerator": "GPU",
        "kernelspec": {"name": "python3", "display_name": "Python 3"},
        "language_info": {"name": "python"},
    },
    "cells": [
        {"cell_type": "markdown", "metadata": {"id": "intro"}, "source": lines(intro)},
        {"cell_type": "code", "metadata": {"id": "start", "cellView": "form"}, "execution_count": None,
         "outputs": [], "source": lines(code)},
        {"cell_type": "markdown", "metadata": {"id": "trouble"}, "source": lines(trouble)},
    ],
}
(here / "SwarAI_ACE-Step_Colab.ipynb").write_text(json.dumps(notebook, indent=1, ensure_ascii=False) + "\n")
print("wrote", here / "SwarAI_ACE-Step_Colab.ipynb")
