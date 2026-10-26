#@title ▶️ Start the free ACE-Step 1.5 server for SwarAI Studio
#@markdown Tap the ▶️ button (or **Runtime → Run all**). First start takes about 10–20 minutes
#@markdown (installing + downloading the model). When it finishes you'll see an **address** and a **password**.
LM_MODEL = "auto"  #@param ["auto", "acestep-5Hz-lm-1.7B", "acestep-5Hz-lm-0.6B", "none"]
#@markdown `auto` picks the song-planner model that fits this machine's memory (0.6B on free Colab, 1.7B on Kaggle).

# This same cell powers both the Google Colab and the Kaggle notebook.
import os, pathlib, re, secrets, shutil, subprocess, sys, time, urllib.request

CPU_TEST = os.environ.get("SWARAI_CPU_TEST") == "1"        # used only by this repo's CI check
LM_MODEL = os.environ.get("SWARAI_LM_MODEL", LM_MODEL)
PLATFORM = os.environ.get("SWARAI_PLATFORM") or (
    "kaggle" if os.environ.get("KAGGLE_KERNEL_RUN_TYPE") or os.path.isdir("/kaggle/working") else "colab")
if PLATFORM == "kaggle":
    # /kaggle/working is limited to ~20 GB and is saved with the notebook; the model and Python env need more.
    default_work = "/kaggle/temp" if os.path.isdir("/kaggle/temp") else "/tmp/swarai"
    GPU_HELP = ("In the notebook's right-hand panel (⋮ → Settings on a phone) set "
                "Accelerator → GPU T4 x2, and turn Internet on, then run again.")
else:
    default_work = "/content"
    GPU_HELP = "In the menu choose Runtime → Change runtime type → T4 GPU → Save, then run again."
WORK = pathlib.Path(os.environ.get("SWARAI_WORKDIR", default_work))
WORK.mkdir(parents=True, exist_ok=True)
REPO = WORK / "ACE-Step-1.5"
PORT = 8001
print("Platform:", PLATFORM, "| working folder:", WORK)

def sh(cmd):
    print("$", cmd, flush=True)
    subprocess.run(cmd, shell=True, check=True)

def step(msg):
    print(f"\n=== {msg} ===", flush=True)

def http_ok(url, key, timeout=10):
    try:
        req = urllib.request.Request(url, headers={"Authorization": f"Bearer {key}"})
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status == 200
    except Exception:
        return False

def tail(path, n=30):
    try:
        return "\n".join(path.read_text(errors="replace").splitlines()[-n:])
    except FileNotFoundError:
        return ""

# 1. GPU
step("1/5 Checking GPU")
gpu = subprocess.run("nvidia-smi --query-gpu=name,memory.total,compute_cap --format=csv,noheader",
                     shell=True, capture_output=True, text=True)
if gpu.returncode != 0 and shutil.which("nvidia-smi"):  # older drivers lack the compute_cap field
    gpu = subprocess.run("nvidia-smi --query-gpu=name,memory.total --format=csv,noheader",
                         shell=True, capture_output=True, text=True)
if gpu.returncode == 0 and gpu.stdout.strip():
    first = gpu.stdout.strip().splitlines()[0]
    print("GPU:", gpu.stdout.strip())
    try:
        compute_cap = float(first.split(",")[-1]) if first.count(",") >= 2 else (0.0 if "P100" in first else 7.5)
    except ValueError:
        compute_cap = 0.0 if "P100" in first else 7.5
    if compute_cap < 7.0:
        raise SystemExit(f"❌ This GPU ({first.split(',')[0]}) is too old for ACE-Step's PyTorch build. "
                         + ("Choose Accelerator → GPU T4 x2 (not P100) and run again." if PLATFORM == "kaggle" else GPU_HELP))
elif CPU_TEST:
    print("No GPU (CI CPU test mode).")
else:
    raise SystemExit("❌ No GPU found. " + GPU_HELP)

# 2. Install ACE-Step (official repo) with uv
step("2/5 Installing ACE-Step 1.5 (first time: ~5–10 min)")
try:
    urllib.request.urlopen("https://github.com", timeout=15)
except Exception:
    raise SystemExit("❌ No internet access. " + ("Turn Internet on in the notebook settings (needs a phone-verified "
                     "Kaggle account), then run again." if PLATFORM == "kaggle" else "Check your connection and run again."))
if not (REPO / "pyproject.toml").exists():
    sh(f"git clone --depth 1 https://github.com/ace-step/ACE-Step-1.5.git {REPO}")
if not shutil.which("ffmpeg"):  # ACE-Step needs ffmpeg to write MP3 files (Colab normally has it)
    sudo = "" if os.geteuid() == 0 else "sudo "
    sh(f"{sudo}apt-get -qq update && {sudo}apt-get -qq install -y ffmpeg > /dev/null")
if not shutil.which("uv"):
    sh(f"{sys.executable} -m pip install -q uv")
sh(f"cd {REPO} && uv sync --quiet")

# 3. Cloudflare quick tunnel binary (free, no account)
step("3/5 Getting Cloudflare tunnel")
CLOUDFLARED = WORK / "cloudflared"
if not CLOUDFLARED.exists():
    sh(f"curl -fsSL -o {CLOUDFLARED} https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64")
    CLOUDFLARED.chmod(0o755)

# 4. Start the API server (password-protected, models loaded before it answers)
step("4/5 Starting ACE-Step server and downloading the model (first time: ~5–10 min)")
key_file = WORK / "swarai_password.txt"
API_KEY = key_file.read_text().strip() if key_file.exists() else secrets.token_urlsafe(18)
key_file.write_text(API_KEY)
# Colab's notebook settings must not leak into the server's own Python environment:
# MPLBACKEND points at a notebook-only matplotlib module, and PYTHONPATH adds Colab's packages.
base_env = {k: v for k, v in os.environ.items() if k not in ("MPLBACKEND", "PYTHONPATH")}
env = dict(base_env,
           MPLBACKEND="Agg",
           CUDA_VISIBLE_DEVICES=os.environ.get("CUDA_VISIBLE_DEVICES", "0").split(",")[0],  # Kaggle T4 x2: use one
           ACESTEP_API_HOST="127.0.0.1", ACESTEP_API_PORT=str(PORT), ACESTEP_API_KEY=API_KEY,
           ACESTEP_NO_INIT="false",                 # load models now, not on the first song
           ACESTEP_CONFIG_PATH="acestep-v15-turbo",  # 2B turbo: fits the free 15 GB T4
           ACESTEP_USE_FLASH_ATTENTION="false",      # T4 (Turing) has no FlashAttention
           ACESTEP_LM_BACKEND="pt")                  # PyTorch LM backend works on T4
if CPU_TEST:
    env["ACESTEP_DEVICE"] = "cpu"

# The song-planner LM must fit in the machine's normal memory (RAM) next to the music model.
# Free Colab has ~12.7 GB RAM: the 1.7B planner gets the server killed there, so "auto" uses 0.6B.
ram_gb = os.sysconf("SC_PAGE_SIZE") * os.sysconf("SC_PHYS_PAGES") / 1024**3
LM_CHAIN = ["acestep-5Hz-lm-1.7B", "acestep-5Hz-lm-0.6B", "none"]
if LM_MODEL == "auto":
    LM_MODEL = LM_CHAIN[0] if ram_gb >= 24 else LM_CHAIN[1]
candidates = LM_CHAIN[LM_CHAIN.index(LM_MODEL):] if LM_MODEL in LM_CHAIN else [LM_MODEL, "none"]
print(f"Machine RAM: {ram_gb:.1f} GB → song planner: {LM_MODEL}")

server_log = WORK / "acestep-server.log"
server = None
for attempt, lm in enumerate(candidates):
    run_env = dict(env)
    if lm == "none":
        run_env["ACESTEP_INIT_LLM"] = "false"
    else:
        run_env["ACESTEP_LM_MODEL_PATH"] = lm
        run_env["ACESTEP_INIT_LLM"] = "true"   # load at startup; the app's "thinking" mode needs it
    for proc_name in ("acestep-api", "cloudflared"):
        subprocess.run(f"pkill -f {proc_name}", shell=True)   # re-running the cell restarts cleanly
    time.sleep(2)
    print(f"Starting server (song planner: {lm})…", flush=True)
    server = subprocess.Popen(f"cd {REPO} && exec uv run acestep-api", shell=True, env=run_env,
                              stdout=open(server_log, "w"), stderr=subprocess.STDOUT)
    started = time.time()
    simulate_kill = os.environ.get("SWARAI_TEST_KILL_FIRST") == "1" and attempt == 0   # CI test of the retry path
    ready = False
    while True:
        if http_ok(f"http://127.0.0.1:{PORT}/health", API_KEY):
            ready = True
            break
        if simulate_kill and time.time() - started > 30:
            print("(CI test: simulating the server being killed for lack of memory)")
            subprocess.run("pkill -9 -f acestep-api", shell=True)
            simulate_kill = False
        if server.poll() is not None:
            break
        if time.time() - started > 60 * 60:
            print(tail(server_log, 60))
            raise SystemExit("❌ Server did not start within 60 minutes.")
        print(f"  …still loading ({int(time.time() - started)} s). Last log line: {tail(server_log, 1)[:150]}", flush=True)
        time.sleep(20)
    if ready:
        break
    log_text = tail(server_log, 60)
    killed = server.returncode in (-9, 137) or "Traceback" not in log_text
    print(log_text)
    reason = ("it was killed without an error message, which almost always means the machine ran out of memory (RAM)"
              if killed else "of the error above")
    if attempt + 1 < len(candidates):
        print(f"\n⚠️ The server stopped because {reason}. Retrying with a smaller song planner: {candidates[attempt + 1]}\n", flush=True)
        continue
    raise SystemExit(f"❌ ACE-Step server stopped because {reason}. Try again later or use the other notebook (Colab/Kaggle).")
print(f"Server ready after {int(time.time() - started)} s.")
try:
    req = urllib.request.Request(f"http://127.0.0.1:{PORT}/health", headers={"Authorization": f"Bearer {API_KEY}"})
    health = __import__("json").loads(urllib.request.urlopen(req, timeout=10).read())["data"]
    print("Model:", health.get("loaded_model"), "| LM:", health.get("loaded_lm_model") or "not loaded (songs still work, without LM planning)")
except Exception as exc:
    print("Could not read server status:", exc)

# 5. Public HTTPS address through Cloudflare
step("5/5 Opening public HTTPS address")
tunnel_log = WORK / "cloudflared.log"
tunnel = subprocess.Popen([str(CLOUDFLARED), "tunnel", "--no-autoupdate", "--url", f"http://127.0.0.1:{PORT}"],
                          stdout=open(tunnel_log, "w"), stderr=subprocess.STDOUT)
PUBLIC_URL = None
for _ in range(90):
    m = re.search(r"https://[a-z0-9-]+\.trycloudflare\.com", tunnel_log.read_text(errors="replace"))
    if m:
        PUBLIC_URL = m.group(0)
        break
    time.sleep(2)
if not PUBLIC_URL:
    print(tail(tunnel_log))
    raise SystemExit("❌ Could not open a Cloudflare tunnel. Run the cell again.")
for _ in range(45):  # new tunnel names take a few seconds to resolve
    if http_ok(f"{PUBLIC_URL}/health", API_KEY):
        break
    time.sleep(4)
(WORK / "swarai_public_url.txt").write_text(PUBLIC_URL)

print("\n" + "=" * 60)
print("✅ READY — paste these into SwarAI Studio")
print("   Settings → ACE-Step server (your PC or free Colab/Kaggle notebook)")
print(f"\n   Address : {PUBLIC_URL}")
print(f"   Password: {API_KEY}")
print("\n   Then tap Save → Test, and select this engine.")
print("=" * 60)
print("Keep this tab open while you make songs. The address changes every time you start the notebook.")

if CPU_TEST or os.environ.get("SWARAI_NO_WAIT") == "1":
    print("(test mode: not waiting)")
else:
    try:
        while True:
            time.sleep(60)
            if server.poll() is not None:
                print(tail(server_log, 40)); raise SystemExit("❌ ACE-Step server stopped (log above). Run the cell again.")
            if tunnel.poll() is not None:
                raise SystemExit("❌ Tunnel closed. Run the cell again (you'll get a new address).")
            print(time.strftime("%H:%M"), "server running —", PUBLIC_URL, flush=True)
    except KeyboardInterrupt:
        server.terminate(); tunnel.terminate(); print("Stopped.")
