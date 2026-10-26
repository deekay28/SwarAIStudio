#@title ▶️ Start the free ACE-Step 1.5 server for SwarAI Studio
#@markdown Tap the ▶️ button (or **Runtime → Run all**). First start takes about 10–20 minutes
#@markdown (installing + downloading the model). When it finishes you'll see an **address** and a **password**.
LM_MODEL = "acestep-5Hz-lm-1.7B"  #@param ["acestep-5Hz-lm-1.7B", "acestep-5Hz-lm-0.6B", "none"]

import os, pathlib, re, secrets, shutil, subprocess, sys, time, urllib.request

CPU_TEST = os.environ.get("SWARAI_CPU_TEST") == "1"        # used only by this repo's CI check
LM_MODEL = os.environ.get("SWARAI_LM_MODEL", LM_MODEL)
WORK = pathlib.Path(os.environ.get("SWARAI_WORKDIR", "/content"))
REPO = WORK / "ACE-Step-1.5"
PORT = 8001

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
gpu = subprocess.run("nvidia-smi --query-gpu=name,memory.total --format=csv,noheader",
                     shell=True, capture_output=True, text=True)
if gpu.returncode == 0:
    print("GPU:", gpu.stdout.strip())
elif CPU_TEST:
    print("No GPU (CI CPU test mode).")
else:
    raise SystemExit("❌ No GPU found. In the menu choose Runtime → Change runtime type → T4 GPU → Save, then run again.")

# 2. Install ACE-Step (official repo) with uv
step("2/5 Installing ACE-Step 1.5 (first time: ~5–10 min)")
if not (REPO / "pyproject.toml").exists():
    sh(f"git clone --depth 1 https://github.com/ace-step/ACE-Step-1.5.git {REPO}")
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
env = dict(os.environ,
           ACESTEP_API_HOST="127.0.0.1", ACESTEP_API_PORT=str(PORT), ACESTEP_API_KEY=API_KEY,
           ACESTEP_NO_INIT="false",                 # load models now, not on the first song
           ACESTEP_CONFIG_PATH="acestep-v15-turbo",  # 2B turbo: fits the free 15 GB T4
           ACESTEP_USE_FLASH_ATTENTION="false",      # T4 (Turing) has no FlashAttention
           ACESTEP_LM_BACKEND="pt")                  # PyTorch LM backend works on T4
if LM_MODEL == "none":
    env["ACESTEP_INIT_LLM"] = "false"
else:
    env["ACESTEP_LM_MODEL_PATH"] = LM_MODEL
if CPU_TEST:
    env["ACESTEP_DEVICE"] = "cpu"

server_log = WORK / "acestep-server.log"
for proc_name in ("acestep-api", "cloudflared"):
    subprocess.run(f"pkill -f {proc_name}", shell=True)   # re-running the cell restarts cleanly
server = subprocess.Popen(f"cd {REPO} && exec uv run acestep-api", shell=True, env=env,
                          stdout=open(server_log, "w"), stderr=subprocess.STDOUT)
started = time.time()
while not http_ok(f"http://127.0.0.1:{PORT}/health", API_KEY):
    if server.poll() is not None:
        print(tail(server_log, 60))
        raise SystemExit("❌ ACE-Step server stopped. The log above shows why.")
    if time.time() - started > 60 * 60:
        print(tail(server_log, 60))
        raise SystemExit("❌ Server did not start within 60 minutes.")
    print(f"  …still loading ({int(time.time() - started)} s). Last log line: {tail(server_log, 1)[:150]}", flush=True)
    time.sleep(20)
print(f"Server ready after {int(time.time() - started)} s.")

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
print("   Settings → ACE-Step server (your PC or free Colab notebook)")
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
