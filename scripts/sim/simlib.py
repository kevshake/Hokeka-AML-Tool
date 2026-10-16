"""Dependency-free HTTP + scenario framework for the Hokeka simulator (Python 3.10+ stdlib only)."""
import json
import os
import re
import ssl
import threading
import time
import traceback
import urllib.error
import urllib.request

PROVIDER_LEAK = re.compile(r"(?i)\blaya\b|laya\.studio|anthropic|openai")
TIPPING_OFF = re.compile(r"(?i)(\bSAR\b|\bSTR\b|suspicious activity report|filing a report|money laundering)")


class Resp:
    def __init__(self, method, url, status, headers, body, elapsed_ms, req_body=None):
        self.method, self.url, self.status, self.headers = method, url, status, headers
        self.raw = body
        self.elapsed_ms = elapsed_ms
        self.req_body = req_body
        self._json = None
        self._parsed = False

    @property
    def text(self):
        try:
            return self.raw.decode("utf-8", "replace")
        except Exception:
            return repr(self.raw[:200])

    @property
    def json(self):
        if not self._parsed:
            self._parsed = True
            try:
                self._json = json.loads(self.raw) if self.raw else None
            except Exception:
                self._json = None
        return self._json

    def header(self, name):
        for k, v in self.headers.items():
            if k.lower() == name.lower():
                return v
        return None

    def evidence(self, limit=600):
        ctype = (self.header("Content-Type") or "")
        body = self.text if ("json" in ctype or "text" in ctype or "xml" in ctype or not ctype) else f"<{len(self.raw)} bytes {ctype}>"
        rb = None
        if self.req_body is not None:
            rb = self.req_body if isinstance(self.req_body, str) else json.dumps(self.req_body)
            rb = rb[:300]
        return {"request": f"{self.method} {self.url}", "requestBody": rb, "status": self.status,
                "ms": round(self.elapsed_ms, 1), "response": body[:limit]}


class Client:
    """One logical user. Keeps its own cookie (JSESSIONID) and simulates the nginx front end by
    sending X-Forwarded-Proto: https from loopback (Control Plane trusts loopback proxies)."""

    def __init__(self, base, name="anon", recorder=None, ssl_context=None, forwarded_https=True):
        self.base = base.rstrip("/")
        self.name = name
        self.cookies = {}
        self.recorder = recorder
        self.ssl_context = ssl_context
        self.forwarded_https = forwarded_https
        self.user = None
        self.lock = threading.Lock()

    def request(self, method, path, body=None, headers=None, raw=None, timeout=60, content_type="application/json"):
        url = path if path.startswith("http") else self.base + path
        h = {"Accept": "application/json, */*"}
        if self.forwarded_https:
            h["X-Forwarded-Proto"] = "https"
        data = None
        if raw is not None:
            data = raw
            h["Content-Type"] = content_type
        elif body is not None:
            data = json.dumps(body).encode()
            h["Content-Type"] = "application/json"
        if self.cookies:
            h["Cookie"] = "; ".join(f"{k}={v}" for k, v in self.cookies.items())
        if headers:
            h.update(headers)
        req = urllib.request.Request(url, data=data, method=method, headers=h)
        t0 = time.perf_counter()
        opener = urllib.request.build_opener(_NoRedirect, urllib.request.HTTPSHandler(context=self.ssl_context)) \
            if self.ssl_context else urllib.request.build_opener(_NoRedirect)
        try:
            with opener.open(req, timeout=timeout) as r:
                status, rh, rb = r.status, dict(r.headers.items()), r.read()
                set_cookies = r.headers.get_all("Set-Cookie") or []
        except urllib.error.HTTPError as e:
            status, rh, rb = e.code, dict(e.headers.items()) if e.headers else {}, e.read() or b""
            set_cookies = (e.headers.get_all("Set-Cookie") if e.headers else None) or []
        except Exception as e:  # connection refused, timeout, TLS failure
            status, rh, rb, set_cookies = 0, {}, f"{type(e).__name__}: {e}".encode(), []
        ms = (time.perf_counter() - t0) * 1000
        for c in set_cookies:
            kv = c.split(";", 1)[0]
            if "=" in kv:
                k, v = kv.split("=", 1)
                with self.lock:
                    if v:
                        self.cookies[k.strip()] = v.strip()
                    else:
                        self.cookies.pop(k.strip(), None)
        resp = Resp(method, url.replace(self.base, ""), status, rh, rb, ms, req_body=body if body is not None else None)
        if self.recorder:
            self.recorder(self, resp)
        return resp

    def get(self, path, **kw):
        return self.request("GET", path, **kw)

    def post(self, path, body=None, **kw):
        return self.request("POST", path, body=body, **kw)

    def put(self, path, body=None, **kw):
        return self.request("PUT", path, body=body, **kw)

    def patch(self, path, body=None, **kw):
        return self.request("PATCH", path, body=body, **kw)

    def delete(self, path, **kw):
        return self.request("DELETE", path, **kw)

    def login(self, username, password):
        self.cookies.clear()
        r = self.post("/auth/login", {"username": username, "password": password})
        if r.status == 200 and r.json:
            self.user = r.json.get("user")
        return r


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **kw):
        return None


class SkipScenario(Exception):
    pass


class Scenario:
    def __init__(self, sid, area, title):
        self.id, self.area, self.title = sid, area, title
        self.checks = []
        self.evidence = []
        self.notes = []
        self.status = "PASS"
        self.error = None
        self.started = time.time()
        self.duration_s = 0

    def check(self, cond, message, resp=None, severity="FAIL"):
        ok = bool(cond)
        entry = {"ok": ok, "message": message, "severity": severity if not ok else None}
        if resp is not None:
            entry["evidence"] = resp.evidence() if isinstance(resp, Resp) else resp
        self.checks.append(entry)
        if not ok:
            if severity == "FAIL":
                self.status = "FAIL"
            elif severity == "WARN" and self.status == "PASS":
                self.status = "WARN"
        return ok

    def note(self, msg):
        self.notes.append(msg)

    def to_dict(self):
        return {"id": self.id, "area": self.area, "title": self.title, "status": self.status,
                "durationSeconds": round(self.duration_s, 2), "error": self.error, "notes": self.notes,
                "checks": self.checks}


class Runner:
    def __init__(self):
        self.scenarios = []
        self.registry = []

    def scenario(self, sid, area, title):
        def deco(fn):
            self.registry.append((sid, area, title, fn))
            return fn
        return deco

    def run(self, ctx, only=None):
        for sid, area, title, fn in self.registry:
            if only and not any(sid.startswith(o) for o in only):
                continue
            sc = Scenario(sid, area, title)
            ctx.current = sc
            print(f"[{sid}] {title} ...", flush=True)
            try:
                fn(ctx, sc)
            except SkipScenario as e:
                sc.status = "SKIP"
                sc.error = str(e)
            except Exception as e:
                sc.status = "ERROR"
                sc.error = f"{type(e).__name__}: {e}\n{traceback.format_exc()[-1500:]}"
            sc.duration_s = time.time() - sc.started
            failed = [c["message"] for c in sc.checks if not c["ok"]]
            print(f"[{sid}] {sc.status} ({len(sc.checks) - len(failed)}/{len(sc.checks)} checks)"
                  + (f" :: {failed[:3]}" if failed else "") + (f" :: {sc.error.splitlines()[0]}" if sc.error else ""), flush=True)
            self.scenarios.append(sc)
        return self.scenarios


def write_results(out_dir, run_id, meta, scenarios):
    os.makedirs(out_dir, exist_ok=True)
    data = {"runId": run_id, "meta": meta, "summary": {}, "scenarios": [s.to_dict() for s in scenarios]}
    for s in scenarios:
        data["summary"][s.status] = data["summary"].get(s.status, 0) + 1
    jpath = os.path.join(out_dir, "sim-results.json")
    with open(jpath, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)
    lines = [f"# Hokeka simulation results — run `{run_id}`", "",
             f"Started: {meta.get('startedAt')}  ·  Finished: {meta.get('finishedAt')}  ·  Control Plane: `{meta.get('base')}`", "",
             "Summary: " + ", ".join(f"**{k}** {v}" for k, v in sorted(data["summary"].items())), "",
             "| ID | Area | Scenario | Status | Checks | Failed checks |", "|---|---|---|---|---|---|"]
    for s in scenarios:
        failed = [c["message"] for c in s.checks if not c["ok"]]
        cell = "; ".join(failed)[:400].replace("|", "\\|") or (s.error.splitlines()[0][:200].replace("|", "\\|") if s.error else "")
        lines.append(f"| {s.id} | {s.area} | {s.title} | {s.status} | {len(s.checks) - len(failed)}/{len(s.checks)} | {cell} |")
    lines += ["", "## Failure evidence", ""]
    for s in scenarios:
        bad = [c for c in s.checks if not c["ok"]]
        if not bad and not s.error:
            continue
        lines.append(f"### {s.id} — {s.title} ({s.status})")
        if s.error:
            lines += ["```", s.error[:1500], "```"]
        for c in bad:
            lines.append(f"- **{c['severity']}** {c['message']}")
            ev = c.get("evidence")
            if ev:
                lines += ["  ```json", "  " + json.dumps(ev)[:1200], "  ```"]
        for n in s.notes:
            lines.append(f"- note: {n}")
        lines.append("")
    lines += ["## Notes per scenario", ""]
    for s in scenarios:
        if s.notes:
            lines.append(f"- **{s.id}**: " + " · ".join(s.notes)[:1500])
    mpath = os.path.join(out_dir, "sim-results.md")
    with open(mpath, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    return jpath, mpath
