"""Generates one short song through a running ACE-Step server using exactly the request the
SwarAI Studio app sends (see AceStepCompanionEngine.kt). Usage: python test_client.py URL PASSWORD OUT_DIR"""
import json
import pathlib
import sys
import time
import urllib.request

url, key, out_dir = sys.argv[1].rstrip("/"), sys.argv[2], pathlib.Path(sys.argv[3])
duration = int(sys.argv[4]) if len(sys.argv) > 4 else 30
headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}


def call(path, body=None, raw=False):
    req = urllib.request.Request(url + path, data=None if body is None else json.dumps(body).encode(),
                                 headers=headers, method="GET" if body is None else "POST")
    with urllib.request.urlopen(req, timeout=120) as r:
        data = r.read()
    return data if raw else json.loads(data)


print("health:", call("/health"))
payload = {  # identical field set to AceReleaseRequest in the app
    "prompt": "Bhojpuri devotional folk song, Chhath Puja, female vocal, warm, dholak, harmonium, shehnai",
    "lyrics": "[verse]\nउगते सूरज के लाली, छठी मईया के घाट\n[chorus]\nजय हो छठी मईया",
    "vocal_language": "hi",
    "audio_duration": duration,
    "audio_format": "mp3",
    "batch_size": 1,
    "thinking": True,
    "use_random_seed": True,
}
task = call("/release_task", payload)
print("release_task:", task)
task_id = task["data"]["task_id"]
start = time.time()
while True:
    q = call("/query_result", {"task_id_list": [task_id]})
    item = next(i for i in q["data"] if i["task_id"] == task_id)
    if item["status"] in (1, 2):
        break
    if time.time() - start > 3600:
        sys.exit("timed out")
    print(f"  waiting… {int(time.time() - start)} s", flush=True)
    time.sleep(15)
print("final item:", json.dumps(item, ensure_ascii=False)[:1500])
if item["status"] != 1:
    sys.exit("generation failed")
results = json.loads(item["result"]) if isinstance(item["result"], str) else item["result"]
results = results if isinstance(results, list) else [results]
audio = call(results[0]["file"] if results[0]["file"].startswith("/") else "/" + results[0]["file"], raw=True)
out_dir.mkdir(parents=True, exist_ok=True)
path = out_dir / "acestep-test.mp3"
path.write_bytes(audio)
assert audio[:3] == b"ID3" or (audio[0] == 0xFF and audio[1] & 0xE0 == 0xE0), "not an MP3"
print(f"saved {path}: {len(audio)} bytes after {int(time.time() - start)} s")
