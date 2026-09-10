"""Explicit HTTP qualification of the packaged reference JAR (includes the 80s route)."""
import json
import os
from pathlib import Path
import socket
import subprocess
import time
from urllib.request import Request, urlopen

with socket.socket() as available:
    available.bind(("127.0.0.1", 0))
    port = available.getsockname()[1]

project = Path(__file__).resolve().parents[1]
environment = {**os.environ, "PORT": str(port), "APP_HOST": "127.0.0.1"}
with (project / "target/http-smoke.log").open("w", encoding="utf-8") as log:
    process = subprocess.Popen(
        ["java", "-Xms16m", "-Xmx256m", "-jar", "target/reference-api.jar"],
        cwd=project, env=environment, stdout=log, stderr=subprocess.STDOUT,
    )
    try:
        def request(path, payload=None, timeout=5):
            data = json.dumps(payload).encode() if payload is not None else None
            req = Request(f"http://127.0.0.1:{port}{path}", data=data,
                          headers={"Content-Type": "application/json"})
            with urlopen(req, timeout=timeout) as response:
                return json.load(response)

        deadline = time.monotonic() + 30
        while True:
            try:
                assert request("/health") == {"status": "ok"}
                break
            except (OSError, AssertionError):
                if process.poll() is not None or time.monotonic() > deadline:
                    raise AssertionError("Packaged API did not start; inspect target/http-smoke.log")
                time.sleep(0.1)
        assert request("/info")["framework"] == "springboot"
        payload = {"message": "reference", "count": 2}
        assert request("/echo", payload) == {"received": payload}
        assert request("/items/7?include_details=true") == {
            "item_id": 7, "include_details": True, "details": "Reference item 7",
        }
        ready = request("/ready", timeout=60)
        assert ready == {"status": "ready", "models": {"text": True, "image": True},
                         "device": "cpu"}
        started = time.monotonic()
        assert request("/slow", timeout=90) == {"delay_seconds": 80, "status": "completed"}
        elapsed = time.monotonic() - started
        assert 79.5 <= elapsed <= 90, elapsed
        print(json.dumps({"http": "passed", "slow_seconds": round(elapsed, 3)}))
    finally:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
