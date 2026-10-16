"""Push the synthetic watchlist (scripts/sim/fixtures.py) into aml-microservice.

Must run BEFORE the Control Plane boots under the `production` profile: SanctionsDataStartupCheck
fails closed on an empty watchlist. Idempotent (entityIds are stable).
Usage: py scripts/sim/stack/seed-watchlist.py [aml_ms_base_url]
"""
import json
import os
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
import fixtures  # noqa: E402


def secrets_env():
    vals = {}
    with open(os.path.join(HERE, "..", "results", "sim-secrets.env"), encoding="utf-8") as fh:
        for line in fh:
            if "=" in line and not line.startswith("#"):
                k, v = line.strip().split("=", 1)
                vals[k] = v
    return vals


def call(base, method, path, key, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + path, data=data, method=method,
                                 headers={"Content-Type": "application/json", "X-Internal-Auth": key})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read() or b"{}")


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8091"
    key = secrets_env()["AML_MS_INTERNAL_KEY"]
    print("ingest:", call(base, "POST", "/internal/v1/sanctions/ingest", key, {"entities": fixtures.WATCHLIST}))
    print("count:", call(base, "GET", "/internal/v1/sanctions/count", key))
    probe = call(base, "POST", "/internal/v1/sanctions/screen", key, {"name": fixtures.SANCTIONED_EXACT, "type": "PERSON"})
    print("probe exact:", json.dumps(probe)[:300])
    if not probe or probe.get("status") in (None, "CLEAR", "NO_MATCH"):
        print("WARNING: exact probe did not match", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
