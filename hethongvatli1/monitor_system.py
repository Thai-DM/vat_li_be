import requests
import time
import json
import sys
import os

BASE_URL = "http://localhost:8080"
OUTPUT_FILE = sys.argv[1] if len(sys.argv) > 1 else "system_metrics.json"

def get_token():
    for _ in range(5):
        try:
            r = requests.post(f"{BASE_URL}/api/v1/users/signin", json={"username": "admin", "password": "admin123456"}, timeout=5)
            if r.status_code == 200:
                return r.json()["data"]["accessToken"]
        except Exception:
            time.sleep(1)
    return None

token = get_token()
if not token:
    print("Could not get admin token for monitoring.")
    sys.exit(1)

headers = {"Authorization": f"Bearer {token}"}

records = []
print(f"Monitoring system metrics to {OUTPUT_FILE} (Ctrl+C or exit file to stop)...")

STOP_FILE = OUTPUT_FILE + ".stop"
if os.path.exists(STOP_FILE):
    os.remove(STOP_FILE)

def query_metric(name):
    try:
        r = requests.get(f"{BASE_URL}/actuator/metrics/{name}", headers=headers, timeout=3)
        if r.status_code == 200:
            m = r.json().get("measurements", [])
            if m:
                return m[0].get("value", 0.0)
    except Exception:
        pass
    return 0.0

try:
    while not os.path.exists(STOP_FILE):
        ts = time.time()
        active_conn = query_metric("hikaricp.connections.active")
        idle_conn = query_metric("hikaricp.connections.idle")
        pending_conn = query_metric("hikaricp.connections.pending")
        jvm_mem = query_metric("jvm.memory.used")
        cpu_proc = query_metric("process.cpu.usage")
        
        record = {
            "timestamp": ts,
            "hikari_active": active_conn,
            "hikari_idle": idle_conn,
            "hikari_pending": pending_conn,
            "jvm_memory_mb": round(jvm_mem / (1024 * 1024), 2),
            "cpu_process_pct": round(cpu_proc * 100, 2)
        }
        records.append(record)
        time.sleep(2)
except KeyboardInterrupt:
    pass

summary = {
    "total_samples": len(records),
    "max_hikari_active": max([r["hikari_active"] for r in records], default=0),
    "max_hikari_pending": max([r["hikari_pending"] for r in records], default=0),
    "avg_hikari_active": round(sum([r["hikari_active"] for r in records]) / max(len(records), 1), 2),
    "max_jvm_memory_mb": max([r["jvm_memory_mb"] for r in records], default=0),
    "avg_jvm_memory_mb": round(sum([r["jvm_memory_mb"] for r in records]) / max(len(records), 1), 2),
    "max_cpu_process_pct": max([r["cpu_process_pct"] for r in records], default=0),
    "avg_cpu_process_pct": round(sum([r["cpu_process_pct"] for r in records]) / max(len(records), 1), 2),
    "samples": records
}

with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
    json.dump(summary, f, indent=2)

if os.path.exists(STOP_FILE):
    os.remove(STOP_FILE)

print(f"Monitoring finished. Saved {len(records)} samples to {OUTPUT_FILE}.")
