#!/usr/bin/env python3
"""Hokeka end-to-end simulator: simulated PSPs driving the Control Plane, aml-microservice and a real
Edge Node. Python 3.10+ standard library only. See scripts/sim/README.md.

  py scripts/sim/sim.py                      run every scenario (fresh run id => rerunnable)
  py scripts/sim/sim.py --only A B E         run scenario groups by id prefix (seeding always runs)
  py scripts/sim/sim.py --no-edge            skip launching an Edge Node
  py scripts/sim/sim.py --volume 400         normal-traffic transactions per PSP (default 200)
"""
import argparse
import concurrent.futures as cf
import datetime as dt
import json
import os
import random
import re
import ssl
import statistics
import subprocess
import sys
import time
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "stack"))

import fixtures  # noqa: E402
import simlib  # noqa: E402
from simlib import Client, SkipScenario, PROVIDER_LEAK, TIPPING_OFF  # noqa: E402

DEFAULT_PW = "Hokeka2026!"          # PermissionInitializer's hard-coded password (finding)
USER_PW = "SimUser!2026-x9"

runner = simlib.Runner()
scenario = runner.scenario


# ───────────────────────────────────────────── context ──────────────────────────────────────────

class Ctx:
    def __init__(self, args):
        self.args = args
        self.base = args.base
        self.proxy = args.proxy
        self.run_id = args.run_id or ("r" + dt.datetime.now().strftime("%m%d%H%M%S"))
        self.out_dir = os.path.join(HERE, "results")
        self.raw_dir = os.path.join(self.out_dir, "runs", self.run_id)
        os.makedirs(self.raw_dir, exist_ok=True)
        self.secrets = load_env(os.path.join(self.out_dir, "sim-secrets.env"))
        self.psp_responses = []      # every response returned to a PSP-scoped session (leak scans)
        self.all_count = 0
        self.raw_log = open(os.path.join(self.raw_dir, "http.jsonl"), "w", encoding="utf-8")
        self.admin = self.client("platform-admin")
        self.psps = {}
        self.platform_checker = None
        self.edge = {}
        self.txns = {}               # tag -> list of (psp_key, response dict)
        self.current = None
        self.started_at = dt.datetime.now().isoformat(timespec="seconds")

    def record(self, client, resp):
        self.all_count += 1
        if getattr(client, "psp_scoped", False):
            self.psp_responses.append((client.name, resp))
        try:
            self.raw_log.write(json.dumps({"who": client.name, **resp.evidence(limit=2000)}) + "\n")
        except Exception:
            pass

    def client(self, name, psp_scoped=False, base=None, ssl_context=None):
        c = Client(base or self.base, name, recorder=self.record, ssl_context=ssl_context)
        c.psp_scoped = psp_scoped
        return c

    def psp(self, key):
        return self.psps[key]


def load_env(path):
    vals = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                if "=" in line and not line.startswith("#"):
                    k, v = line.strip().split("=", 1)
                    vals[k] = v
    return vals


def psql(sql):
    p = subprocess.run(["docker", "exec", "-i", "hokeka-sim-postgres-1", "psql", "-U", "hokeka_sim", "-d",
                        "fraud_detector_sim", "-At", "-c", sql], capture_output=True, text=True)
    if p.returncode != 0:
        raise RuntimeError("psql failed: " + p.stderr)
    return p.stdout.strip()


def items(resp):
    """Return a list from a list / Page / KeysetPage JSON body."""
    j = resp.json
    if isinstance(j, list):
        return j
    if isinstance(j, dict):
        for k in ("content", "items", "data", "results", "alerts", "cases"):
            if isinstance(j.get(k), list):
                return j[k]
    return []


def wait_until(fn, timeout=30, interval=1.0):
    end = time.time() + timeout
    last = None
    while time.time() < end:
        last = fn()
        if last:
            return last
        time.sleep(interval)
    return last


def pan_for(tag):
    rnd = random.Random(tag)
    return "4" + "".join(str(rnd.randint(0, 9)) for _ in range(15))


# ───────────────────────────────────────────── seeding ──────────────────────────────────────────

def seed(ctx):
    sc = simlib.Scenario("S0", "seed", "Seed PSPs, users, roles, merchants")
    ctx.current = sc
    r = ctx.admin.login("admin", ctx.secrets.get("SEED_ADMIN_PASSWORD", ""))
    if r.status != 200:
        sc.note("admin did not accept SEED_ADMIN_PASSWORD; falling back to the hard-coded default")
        r = ctx.admin.login("admin", DEFAULT_PW)
    if r.status != 200:
        raise SystemExit(f"cannot log in as platform admin: {r.status} {r.text[:200]}")
    roles = {x["name"]: x["id"] for x in items(ctx.admin.get("/roles")) if not x.get("psp")}
    ctx.global_roles = roles

    # A second platform operator so maker/checker flows have a distinct checker.
    checker_name = f"sim.checker.{ctx.run_id}"
    r = ctx.admin.post("/users", {"username": checker_name, "email": f"{checker_name}@sim.hokeka.test",
                                  "firstName": "Sim", "lastName": "Checker", "password": USER_PW,
                                  "roleId": roles.get("ADMIN")})
    sc.check(r.status in (200, 201), "create platform checker user", r)
    ctx.platform_checker = ctx.client("platform-checker")
    sc.check(ctx.platform_checker.login(checker_name, USER_PW).status == 200, "platform checker login")

    for spec in fixtures.PSPS:
        code = f"{spec['code_prefix']}_{ctx.run_id}".upper()
        r = ctx.admin.post("/psps/register", {
            "pspCode": code, "legalName": f"{spec['legalName']} {ctx.run_id}", "tradingName": spec["tradingName"],
            "country": spec["country"], "contactEmail": f"ops@{spec['key']}.{ctx.run_id}.sim.test",
            "billingPlan": "PAY_AS_YOU_GO", "currency": spec["currency"]})
        sc.check(r.status in (200, 201), f"register PSP {code}", r)
        body = r.json or {}
        psp_id = body.get("id") or body.get("pspId") or (body.get("psp") or {}).get("id")
        if not psp_id:
            raise SystemExit(f"PSP register returned no id: {r.text[:300]}")
        p = {"key": spec["key"], "id": psp_id, "code": code, "spec": spec, "users": {}, "merchants": [],
             "status_after_register": body.get("status")}
        r = ctx.admin.put(f"/psps/{psp_id}/status", {"status": "ACTIVE"})
        sc.check(r.status in (200, 204), f"activate PSP {code}", r)

        # Per-PSP PSP_USER role (there is no global PSP_USER template).
        r = ctx.admin.post("/roles", {"name": "PSP_USER", "description": "Sim PSP operator", "pspId": psp_id,
                                      "permissions": ["VIEW_CASES", "MERCHANT_VIEW", "REPORT_VIEW",
                                                      "VIEW_TRANSACTION_DETAILS"]})
        sc.check(r.status in (200, 201), f"create per-PSP PSP_USER role for {code}", r)
        psp_user_role = (r.json or {}).get("id")
        p["psp_user_role"] = psp_user_role

        for ukey, role_id in (("admin", roles.get("PSP_ADMIN")), ("compliance", roles.get("COMPLIANCE_OFFICER")),
                              ("analyst", roles.get("PSP_ANALYST")), ("user", psp_user_role)):
            uname = f"sim.{spec['key']}.{ukey}.{ctx.run_id}"
            r = ctx.admin.post("/users", {"username": uname, "email": f"{uname}@sim.hokeka.test",
                                          "firstName": spec["key"].title(), "lastName": ukey.title(),
                                          "password": USER_PW, "roleId": role_id, "pspId": psp_id})
            ok = sc.check(r.status in (200, 201), f"create {ukey} user for {code}", r)
            uid = (r.json or {}).get("id") if ok else None
            cl = ctx.client(f"{spec['key']}-{ukey}", psp_scoped=True)
            lr = cl.login(uname, USER_PW)
            sc.check(lr.status == 200, f"{uname} login", lr)
            p["users"][ukey] = {"username": uname, "id": uid, "client": cl}

        for i, m in enumerate(spec["merchants"]):
            r = ctx.admin.post("/merchants", {"legalName": f"{m['name']} {ctx.run_id}", "tradingName": m["name"],
                                              "registrationNumber": f"SIM-{spec['key']}-{i}-{ctx.run_id}",
                                              "country": m["country"], "mcc": m["mcc"], "pspId": psp_id,
                                              "contactEmail": f"m{i}@{spec['key']}.sim.test"})
            ok = sc.check(r.status in (200, 201), f"create merchant {m['name']} for {code}", r)
            if ok:
                mid = (r.json or {}).get("merchantId") or (r.json or {}).get("id")
                p["merchants"].append({"id": mid, **m, "status": (r.json or {}).get("status")})
        ctx.psps[spec["key"]] = p
    sc.duration_s = time.time() - sc.started
    return sc


def merchant(ctx, key, mcc=None, idx=0):
    ms = ctx.psp(key)["merchants"]
    if mcc:
        ms = [m for m in ms if m["mcc"] == mcc]
    if not ms:
        raise SkipScenario(f"no merchant for {key} mcc={mcc}")
    return ms[idx]


def txn_body(ctx, key, amount_cents, mcc=None, **kw):
    m = merchant(ctx, key, mcc)
    spec = ctx.psp(key)["spec"]
    body = {"merchantId": str(m["id"]), "amountCents": int(amount_cents), "currency": kw.pop("currency", spec["currency"]),
            "pan": kw.pop("pan", pan_for(uuid.uuid4().hex)), "terminalId": kw.pop("terminalId", f"T-{key}-01"),
            "ipAddress": kw.pop("ipAddress", f"198.51.100.{random.randint(2, 250)}"), "countryCode": kw.pop("countryCode", "KE"),
            "channelType": kw.pop("channelType", "ECOM"), "direction": kw.pop("direction", "INBOUND"),
            "clientReference": kw.pop("clientReference", f"{ctx.run_id}-{uuid.uuid4().hex[:12]}"),
            "acquirerResponse": kw.pop("acquirerResponse", "00")}
    body.update(kw)
    return body


def ingest(ctx, key, body, who="admin", headers=None):
    cl = ctx.psp(key)["users"][who]["client"]
    h = {"Idempotency-Key": body.get("clientReference")} if body.get("clientReference") else {}
    if headers:
        h.update(headers)
    return cl.post("/transactions/ingest", body, headers=h)


def remember(ctx, tag, key, resp):
    ctx.txns.setdefault(tag, []).append((key, resp.json if isinstance(resp.json, dict) else {"status": resp.status}))


def flagged(ctx, key, resp, wait=6):
    """True when the txn was HOLD/BLOCK or produced at least one alert."""
    j = resp.json or {}
    if j.get("action") in ("HOLD", "BLOCK"):
        return True, j.get("action"), []
    txn_id = j.get("txnId")
    if not txn_id:
        return False, None, []
    cl = ctx.psp(key)["users"]["admin"]["client"]

    def alerts():
        r = cl.get(f"/transactions/{txn_id}/alerts")
        return items(r) if r.status == 200 else None
    found = wait_until(alerts, timeout=wait, interval=1.5)
    return bool(found), j.get("action"), found or []


# ───────────────────────────────────────────── A: platform / health ─────────────────────────────

@scenario("A1", "monitoring", "Actuator health / info / metrics exposure")
def a1(ctx, sc):
    anon = ctx.client("anon")
    r = anon.get("/actuator/health")
    sc.check(r.status == 200 and (r.json or {}).get("status") == "UP", "anonymous /actuator/health is UP", r)
    sc.check("components" not in (r.json or {}), "anonymous health hides component detail", r)
    r = ctx.admin.get("/actuator/health")
    comps = (r.json or {}).get("components", {})
    sc.note("health components: " + ", ".join(f"{k}={v.get('status')}" for k, v in comps.items()))
    for name, v in comps.items():
        sc.check(v.get("status") == "UP", f"health component {name} UP", {"component": name, "detail": v}, severity="WARN")
    sc.check(anon.get("/actuator/metrics").status in (401, 403), "anonymous /actuator/metrics refused")
    sc.check(anon.get("/actuator/prometheus").status in (401, 403), "anonymous /actuator/prometheus refused")
    r = ctx.admin.get("/actuator/prometheus")
    sc.check(r.status == 200 and "jvm_" in r.text, "admin can scrape /actuator/prometheus", r)
    u = ctx.psp("card")["users"]["user"]["client"]
    sc.check(u.get("/actuator/prometheus").status in (401, 403), "PSP user cannot scrape prometheus")


@scenario("A2", "monitoring", "Startup configuration warnings (Control Plane log)")
def a2(ctx, sc):
    log = os.path.join(HERE, "results", "logs", "control-plane.log")
    if not os.path.exists(log):
        raise SkipScenario("control-plane.log not found (CP not started by the sim scripts)")
    with open(log, encoding="utf-8", errors="replace") as fh:
        text = fh.read()
    start = text.rfind("Starting AmlFraudDetectorApplication")
    seg = text[start:] if start >= 0 else text
    end = seg.find("Started AmlFraudDetectorApplication")
    boot = seg[: end + 200] if end >= 0 else seg
    clean = re.sub(r"\x1b\[[0-9;]*m", "", boot)
    warn = [l for l in clean.splitlines() if " WARN " in l and "DefaultSqlScriptExecutor" not in l]
    err = [l for l in clean.splitlines() if " ERROR " in l]
    sc.note(f"boot WARN lines (excluding Flyway 'already exists'): {len(warn)}; ERROR lines: {len(err)}")
    for l in warn[:15] + err[:10]:
        sc.note(l[:260])
    sc.check("EPHEMERAL" not in clean, "edge HSE-1 keys are configured (no EPHEMERAL key warning)")
    # The validator runs on ApplicationReadyEvent, i.e. just after the "Started" line.
    m = re.search(r"RESULT: (\d+) ok, (\d+) warn, (\d+) skipped, (\d+) MISSING", re.sub(r"\x1b\[[0-9;]*m", "", seg))
    sc.check(m and m.group(4) == "0", "EnvVarStartupValidator reports 0 MISSING", {"line": m.group(0) if m else None})
    flyway_dupe = len([l for l in clean.splitlines() if "already exists, skipping" in l])
    sc.check(flyway_dupe == 0, f"Flyway migrations are free of duplicate DDL ({flyway_dupe} 'already exists' notices)",
             severity="WARN")
    sc.check(not err, "no ERROR lines during boot", {"errors": [e[:300] for e in err[:5]]}, severity="WARN")
    sc.check("Timeout on blocking read" not in clean, "sanctions startup count check did not time out",
             {"hint": "SanctionsCountClient 500ms timeout turns the fail-closed check into a warning"}, severity="WARN")
    ver = psql("select max(version::int) from flyway_schema_history where version ~ '^[0-9]+$'")
    failed = psql("select count(*) from flyway_schema_history where not success")
    sc.check(failed == "0", f"Flyway: all migrations successful (latest V{ver})", {"latest": ver, "failed": failed})


@scenario("A3", "schema", "Fresh Flyway schema needs no out-of-band patches")
def a3(ctx, sc):
    # Detects (read-only) against the live database; the sim never patches the schema any more.
    import db_workarounds
    defects = db_workarounds.detect()
    sc.check(not defects, f"no schema defects in the fresh Flyway schema ({len(defects)} found)",
             {"defects": [d["defect"] for d in defects][:30]})


# ───────────────────────────────────────────── B: auth ──────────────────────────────────────────

@scenario("B1", "auth", "Login / me / logout / session")
def b1(ctx, sc):
    p = ctx.psp("card")
    u = p["users"]["user"]
    c = ctx.client("card-user-b1", psp_scoped=True)
    r = c.login(u["username"], "wrong-password")
    sc.check(r.status == 401, "bad password -> 401", r)
    r = c.login(u["username"], USER_PW)
    sc.check(r.status == 200 and (r.json or {}).get("user", {}).get("pspId") == p["id"], "login returns user bound to PSP", r)
    sc.check("JSESSIONID" in c.cookies, "session cookie issued")
    r = c.get("/auth/me")
    sc.check(r.status == 200 and (r.json or {}).get("username") == u["username"], "/auth/me", r)
    r = c.get("/auth/session/check")
    sc.check(r.status == 200 and (r.json or {}).get("valid") is True, "/auth/session/check valid", r)
    r = c.post("/auth/logout")
    sc.check(r.status in (200, 204), "logout", r)
    r = c.get("/auth/me")
    sc.check(r.status in (401, 403), "session invalid after logout", r)
    anon = ctx.client("anon")
    r = anon.get("/alerts")
    sc.check(r.status in (401, 403), "unauthenticated /alerts refused", r)
    r = anon.post("/auth/login", {"username": u["username"], "password": USER_PW},
                  headers={"X-Forwarded-Proto": "http"})
    sc.check(r.status in (301, 302, 307, 308, 400, 403), "plain-HTTP login refused (require-ssl)", r)
    # Auth contract per docs/PSP_API_GUIDE.md §2: Console = session cookie; machines = PSP API key.
    # A Bearer value that is not an hka_ API key (e.g. the session id) must not authenticate.
    c2 = ctx.client("card-user-bearer", psp_scoped=True)
    lr = c2.login(u["username"], USER_PW)
    token = (lr.json or {}).get("token")
    if token:
        c3 = ctx.client("card-user-bearer-only", psp_scoped=True)
        r = c3.get("/auth/me", headers={"Authorization": f"Bearer {token}"})
        sc.check(r.status in (401, 403), "non-API-key Bearer value is not accepted (session/API-key contract)", r)


@scenario("B2", "auth", "Hard-coded / demo credentials are not usable in production profile")
def b2(ctx, sc):
    for user in ("admin", "super.admin", "platform.admin", "velocity.admin", "compliance", "analyst", "viewer"):
        c = ctx.client(f"default-cred-{user}")
        r = c.login(user, DEFAULT_PW)
        sc.check(r.status != 200, f"'{user}' / '{DEFAULT_PW}' must not log in on the production profile", r)
    c = ctx.client("seed-admin")
    r = c.login("admin", ctx.secrets.get("SEED_ADMIN_PASSWORD", ""))
    sc.check(r.status == 200, "admin logs in with SEED_ADMIN_PASSWORD (operator-controlled secret)", r)


@scenario("B3", "auth", "Password reset + change password")
def b3(ctx, sc):
    anon = ctx.client("anon")
    p = ctx.psp("momo")
    u = p["users"]["user"]
    r1 = anon.post("/auth/password-reset/request", {"identifier": u["username"]})
    r2 = anon.post("/auth/password-reset/request", {"identifier": f"nobody-{ctx.run_id}"})
    sc.check(r1.status == 200 and r2.status == 200, "reset request returns 200 for known and unknown users", r1)
    sc.check(r1.text == r2.text, "reset response does not reveal whether the user exists", {"known": r1.text, "unknown": r2.text})
    r = anon.post("/auth/password-reset/confirm", {"token": "not-a-real-token"})
    sc.check(400 <= r.status < 500, "invalid reset token rejected", r)
    tok = psql(f"select count(*) from password_reset_tokens t join platform_users u on u.id=t.user_id where u.username='{u['username']}'") \
        if psql("select to_regclass('public.password_reset_tokens')") else ""
    sc.note(f"reset tokens stored for user: {tok or 'n/a (table not found)'}")
    r = anon.post("/auth/password-reset/emergency", {"identifier": u["username"]}, headers={"X-EMERGENCY-RESET-SECRET": "guess"})
    sc.check(r.status in (403, 404, 400, 401), "emergency reset disabled / refused", r)
    c = u["client"]
    newpw = USER_PW + "#2"
    r = c.put("/users/me/password", {"currentPassword": "wrong", "newPassword": newpw})
    sc.check(r.status in (400, 401, 403), "change password with wrong current password refused", r)
    r = c.put("/users/me/password", {"currentPassword": USER_PW, "newPassword": newpw})
    sc.check(r.status in (200, 204), "change own password", r)
    c2 = ctx.client("momo-user-newpw", psp_scoped=True)
    sc.check(c2.login(u["username"], newpw).status == 200, "login with new password")
    r = c.put("/users/me/password", {"currentPassword": newpw, "newPassword": USER_PW})
    sc.check(r.status in (200, 204), "restore password", r)
    u["client"].login(u["username"], USER_PW)


# ───────────────────────────────────────────── C: RBAC / tenant isolation ───────────────────────

@scenario("C1", "rbac", "PSP users cannot reach platform endpoints")
def c1(ctx, sc):
    card = ctx.psp("card")
    for ukey in ("user", "analyst", "admin"):
        c = card["users"][ukey]["client"]
        checks = [("GET", "/admin/psp-api-keys?pspId=%d" % card["id"]), ("POST", "/psps/register"),
                  ("PUT", f"/psps/{card['id']}/status"), ("GET", "/admin/onboarding/invites"),
                  ("PUT", f"/psps/{card['id']}/signal-settings"), ("GET", "/settings/system"),
                  ("GET", "/ai/status"), ("PUT", "/ai/engines/FRAUD_SCORING"), ("GET", "/admin/psp-billing/summary"),
                  ("POST", "/billing/invoices/generate"), ("GET", "/admin/decision-orchestrator")]
        for method, path in checks:
            body = {} if method in ("POST", "PUT") else None
            r = c.request(method, path, body=body)
            sc.check(r.status in (401, 403, 404, 405), f"{ukey}: {method} {path} refused", r)
    u = card["users"]["user"]["client"]
    r = u.get("/psps")
    sc.check(r.status in (401, 403), "PSP user cannot list all PSPs", r)
    r = u.post("/rules", {"name": "x", "ruleType": "JSON", "ruleJson": "{}", "action": "ALLOW"})
    sc.check(r.status in (401, 403), "PSP_USER without MANAGE_RULES cannot create rules", r)


@scenario("C2", "tenant", "PSP A cannot see or act on PSP B data")
def c2(ctx, sc):
    a, b = ctx.psp("card"), ctx.psp("momo")
    ca = a["users"]["admin"]["client"]
    r = ca.get(f"/psps/{b['id']}")
    sc.check(r.status in (403, 404), "PSP A admin GET /psps/{B} refused", r)
    r = ca.get(f"/psps/{b['id']}/signal-settings")
    sc.check(r.status in (403, 404), "PSP A admin GET B signal-settings refused", r)
    r = ca.get(f"/merchants?pspId={b['id']}")
    leaked = [m for m in items(r) if str(m.get("pspId") or (m.get("psp") or {}).get("id")) == str(b["id"])] if r.status == 200 else []
    b_names = {m["name"] for m in b["merchants"]}
    leaked += [m for m in items(r) if (m.get("tradingName") or "") in b_names]
    sc.check(r.status in (403, 404) or not leaked, "PSP A cannot list PSP B merchants", r)
    r = ca.get(f"/merchants/{b['merchants'][0]['id']}")
    sc.check(r.status in (403, 404), "PSP A cannot read PSP B merchant by id", r)
    r = ca.get(f"/users?pspId={b['id']}")
    bu = [x for x in items(r) if str(x.get("username", "")).startswith(f"sim.momo.")]
    sc.check(r.status in (403, 404) or not bu, "PSP A admin cannot list PSP B users", r)
    r = ca.get(f"/edge/nodes?pspId={b['id']}")
    sc.check(r.status in (403, 404) or not [n for n in items(r) if n.get("pspId") == b["id"]] and
             not [n for n in (r.json if isinstance(r.json, list) else []) if n.get("pspId") == b["id"]],
             "PSP A cannot list PSP B edge nodes", r)
    # Cross-tenant ingest: card admin posts against a momo merchant.
    body = txn_body(ctx, "momo", 1000)
    r = ingest(ctx, "card", body)
    sc.check(r.status in (400, 403, 404), "PSP A cannot ingest against PSP B's merchant", r)
    if r.status == 200 and (r.json or {}).get("txnId"):
        owner = psql(f"select psp_id from transactions where txn_id={int(r.json['txnId'])}") if str(r.json["txnId"]).isdigit() else "?"
        sc.note(f"cross-tenant ingest accepted; stored psp_id={owner} (card={a['id']}, momo={b['id']})")
    # B's transaction is not visible to A
    rb = ingest(ctx, "momo", txn_body(ctx, "momo", 1200))
    tid = (rb.json or {}).get("txnId")
    if tid:
        r = ca.get(f"/transactions/{tid}")
        sc.check(r.status in (403, 404), "PSP A cannot read PSP B transaction by id", r)
        r = ca.get(f"/transactions/{tid}/alerts")
        sc.check(r.status in (403, 404) or not items(r), "PSP A cannot read PSP B transaction alerts", r)
    r = ca.get(f"/transactions?pspId={b['id']}&size=50")
    other = [t for t in items(r) if str(t.get("pspId")) == str(b["id"])]
    sc.check(r.status in (403, 404) or not other, "PSP A cannot page PSP B transactions via pspId param", r)
    r = ca.get(f"/billing/usage/{b['id']}/current")
    sc.check(r.status in (403, 404), "PSP A cannot read PSP B billing usage", r)
    r = ca.get(f"/compliance/cases?size=100")
    sc.note(f"PSP A /compliance/cases -> {r.status}")
    r = ca.get(f"/reporting/schemes/pack?type=CENTRAL_BANK&startDate={dt.date.today()}&endDate={dt.date.today()}&format=JSON&pspId={b['id']}")
    sc.check(not (r.status == 200 and str(b["id"]) in json.dumps((r.json or {}).get("pspId", ""))),
             "scheme pack ignores foreign pspId", r)


@scenario("C3", "tenant", "PSP B users see only their own alerts / transactions")
def c3(ctx, sc):
    for key in ("card", "momo", "vasp"):
        p = ctx.psp(key)
        c = p["users"]["admin"]["client"]
        r = c.get("/alerts?size=100")
        foreign = [x for x in items(r) if x.get("pspId") not in (None, p["id"])]
        sc.check(r.status == 200 and not foreign, f"{key}: /alerts contains only own PSP", r)
        r = c.get("/transactions?size=100")
        foreign = [x for x in items(r) if x.get("pspId") not in (None, p["id"])]
        sc.check(r.status == 200 and not foreign, f"{key}: /transactions contains only own PSP", r)
        r = c.get("/compliance/sar?size=100")
        foreign = [x for x in items(r) if x.get("pspId") not in (None, p["id"])]
        sc.check(r.status in (200, 403) and not foreign, f"{key}: /compliance/sar contains only own PSP", r)


# ───────────────────────────────────────────── D: onboarding / keys / settings / billing ────────

@scenario("D1", "onboarding", "Invite-only onboarding")
def d1(ctx, sc):
    p = ctx.psp("vasp")
    exp = (dt.datetime.now() + dt.timedelta(days=2)).replace(microsecond=0).isoformat()
    r = ctx.admin.post("/admin/onboarding/invites", {"pspId": p["id"], "role": "PSP_ANALYST", "expiresAt": exp})
    sc.check(r.status in (200, 201) and (r.json or {}).get("token"), "platform admin creates invite", r)
    token = (r.json or {}).get("token")
    if not token:
        return
    uname = f"sim.vasp.invited.{ctx.run_id}"
    anon = ctx.client("invitee", psp_scoped=True)
    r = anon.post("/auth/register-with-invite", {"username": uname, "email": f"{uname}@sim.hokeka.test", "firstName": "In",
                                                 "lastName": "Vited", "password": USER_PW, "inviteToken": token})
    sc.check(r.status in (200, 201), "register with invite", r)
    lr = anon.login(uname, USER_PW)
    sc.check(lr.status == 200 and (lr.json or {}).get("user", {}).get("pspId") == p["id"], "invited user bound to invite PSP", lr)
    r = ctx.client("anon2").post("/auth/register-with-invite", {"username": uname + "x", "email": f"x{uname}@sim.hokeka.test",
                                                                 "firstName": "a", "lastName": "b", "password": USER_PW, "inviteToken": token})
    sc.check(r.status in (400, 403, 409), "invite cannot be reused", r)
    r = ctx.client("anon3").post("/auth/register", {"username": "selfsignup" + ctx.run_id, "email": f"s{ctx.run_id}@x.test",
                                                    "firstName": "a", "lastName": "b", "password": USER_PW})
    sc.check(r.status in (401, 403), "anonymous self-registration refused (invite-only)", r)
    r = ctx.client("anon4").post("/psps/register", {"pspCode": "ANON" + ctx.run_id})
    sc.check(r.status in (401, 403), "anonymous PSP registration refused", r)
    # PSP admin inviting into another PSP
    ca = ctx.psp("card")["users"]["admin"]["client"]
    r = ca.post("/admin/onboarding/invites", {"pspId": p["id"], "role": "PSP_ADMIN", "expiresAt": exp})
    sc.check(r.status in (401, 403), "PSP admin cannot mint invites (or for another PSP)", r)
    # Freshly registered PSP status
    sc.check(ctx.psp("card")["status_after_register"] in ("PENDING", None), "new PSP starts PENDING until activated",
             {"status": ctx.psp("card")["status_after_register"]}, severity="WARN")


@scenario("D2", "onboarding", "PSP API keys lifecycle and use")
def d2(ctx, sc):
    p = ctx.psp("card")
    r = ctx.admin.post(f"/admin/psp-api-keys?pspId={p['id']}")
    sc.check(r.status in (200, 201) and str((r.json or {}).get("plaintextKey", "")).startswith("hka_"), "issue API key", r)
    key = (r.json or {}).get("plaintextKey")
    kid = (r.json or {}).get("id")
    r = ctx.admin.get(f"/admin/psp-api-keys?pspId={p['id']}")
    listed = items(r)
    sc.check(r.status == 200 and listed and all("plaintextKey" not in k for k in listed), "list keys hides plaintext", r)
    stored = psql(f"select count(*) from psp_api_keys where key_hash like '%{key[4:20]}%'") if key and psql("select to_regclass('public.psp_api_keys')") else "0"
    sc.check(stored == "0", "plaintext key not stored in DB", {"matches": stored})
    if key:
        anon = ctx.client("apikey-client", psp_scoped=True)
        body = txn_body(ctx, "card", 1500)
        r1 = anon.post("/transactions/ingest", body, headers={"X-Api-Key": key})
        r2 = anon.post("/transactions/ingest", body, headers={"Authorization": f"Bearer {key}"})
        sc.check(r1.status == 200 or r2.status == 200, "issued API key authenticates PSP ingest (X-Api-Key or Bearer)",
                 r1 if r1.status != 200 else r2)
        r = anon.post("/transactions/ingest", txn_body(ctx, "momo", 1500), headers={"X-Api-Key": key})
        sc.check(r.status in (403, 404), "card API key cannot ingest against another PSP's merchant", r)
        sc.check("JSESSIONID" not in anon.cookies, "API-key ingest creates no HTTP session (stateless machine auth)",
                 {"cookies": list(anon.cookies)})
        r = anon.get("/alerts", headers={"X-Api-Key": key})
        sc.check(r.status in (401, 403), "API key is limited to machine ingest (no Console reads)", r)
        r = anon.post("/transactions/ingest", txn_body(ctx, "card", 1500), headers={"X-Api-Key": key[:-2] + "zz"})
        sc.check(r.status == 401, "tampered API key -> 401", r)
    if kid:
        r = ctx.admin.post(f"/admin/psp-api-keys/{kid}/rotate")
        sc.check(r.status in (200, 201) and (r.json or {}).get("plaintextKey") not in (None, key), "rotate key", r)
        rotated = (r.json or {}).get("plaintextKey")
        # Rotation issues a NEW key row (new id) and revokes the old one; revoke the rotated key.
        kid = (r.json or {}).get("id") or kid
        if rotated and key:
            r = ctx.client("apikey-old").post("/transactions/ingest", txn_body(ctx, "card", 1500), headers={"X-Api-Key": key})
            sc.check(r.status == 401, "pre-rotation key no longer authenticates", r)
        r = ctx.admin.delete(f"/admin/psp-api-keys/{kid}")
        sc.check(r.status in (200, 204), "revoke key", r)
        if rotated:
            r = ctx.client("apikey-revoked").post("/transactions/ingest", txn_body(ctx, "card", 1500),
                                                  headers={"X-Api-Key": rotated})
            sc.check(r.status == 401, "revoked key no longer authenticates", r)


@scenario("D3", "onboarding", "PSP settings & signal taxonomy mode")
def d3(ctx, sc):
    for key, mode in (("card", "REPORTING_ONLY"), ("momo", "INFLUENCE_DECISION"), ("vasp", "ALERT_ROUTING")):
        p = ctx.psp(key)
        r = ctx.admin.put(f"/psps/{p['id']}/signal-settings", {"mode": mode})
        sc.check(r.status in (200, 204), f"set {key} signal mode {mode}", r)
        r = p["users"]["admin"]["client"].get(f"/psps/{p['id']}/signal-settings")
        sc.check(r.status == 200 and (r.json or {}).get("mode") == mode, f"{key} PSP admin reads own signal mode", r)
    r = ctx.admin.put(f"/psps/{ctx.psp('card')['id']}/signal-settings", {"mode": "NOT_A_MODE"})
    sc.check(r.status == 400, "invalid signal mode rejected with 400", r)
    c = ctx.psp("card")["users"]["admin"]["client"]
    r = c.get("/settings")
    sc.check(r.status == 200, "PSP admin GET /settings", r)
    r = c.get("/psps/me")
    sc.check(r.status == 200 and (r.json or {}).get("id") == ctx.psp("card")["id"], "GET /psps/me returns own PSP", r)
    r = c.put(f"/psps/{ctx.psp('card')['id']}", {"contactPhone": "+254700000001", "primaryColor": "#123456"})
    sc.check(r.status in (200, 204), "PSP admin updates own PSP profile", r)
    r = ctx.admin.get("/settings/system")
    sc.check(r.status == 200, "platform admin GET /settings/system", r)


@scenario("D4", "billing", "Billing, usage, subscriptions, invoices")
def d4(ctx, sc):
    p = ctx.psp("card")
    c = p["users"]["admin"]["client"]
    r = c.get(f"/billing/rates?pspId={ctx.psp('momo')['id']}&serviceType=TRANSACTION_MONITORING")
    sc.check(r.status in (403, 404), "PSP admin cannot read another PSP's negotiated rate", r)
    for path in (f"/billing/rates?pspId={p['id']}&serviceType=TRANSACTION_MONITORING", f"/billing/usage/{p['id']}/current", f"/subscriptions/psp/{p['id']}", "/entitlements/me",
                 "/billing/invoices"):
        r = c.get(path)
        sc.check(r.status == 200, f"PSP admin GET {path}", r)
    r = ctx.admin.get("/admin/psp-billing/summary")
    sc.check(r.status == 200, "platform billing summary", r)
    r = ctx.admin.get(f"/admin/psp-billing/{p['id']}/usage")
    sc.check(r.status == 200, "platform per-PSP usage", r)
    r = ctx.admin.post(f"/admin/psp-billing/{p['id']}/invoice/generate", {})
    sc.check(r.status in (200, 201), "generate invoice for PSP", r)
    inv = (r.json or {}).get("id") or (r.json or {}).get("invoiceId")
    if inv:
        r = ctx.admin.get(f"/billing/invoices/{inv}/pdf")
        sc.check(r.status == 200 and r.raw[:4] == b"%PDF", "invoice PDF renders", r)
    r = ctx.admin.get(f"/admin/psps/{p['id']}/billing-rates")
    sc.check(r.status == 200, "list billing rates for PSP", r)


# ───────────────────────────────────────────── R: rules ─────────────────────────────────────────

RULE_AMOUNT = 250_000   # cents


@scenario("R1", "rules", "Rule CRUD with visual-builder JSON + maker/checker + live effect")
def r1(ctx, sc):
    p = ctx.psp("card")
    maker = p["users"]["admin"]["client"]
    rule_json = {"conditions": {"all": [{"field": "amount", "operator": ">", "value": RULE_AMOUNT},
                                        {"field": "mcc", "operator": "IN", "value": ["5732"]}]}}
    r = maker.post("/rules/validate", {"ruleJson": json.dumps(rule_json)})
    sc.check(r.status == 200 and (r.json or {}).get("edgeDeployable") is True, "visual rule JSON validates as edge-deployable", r)
    bad = maker.post("/rules/validate", {"ruleJson": json.dumps({"conditions": {"field": "nope", "operator": "~~", "value": 1}})})
    sc.check(bad.status == 400, "invalid rule JSON rejected", bad)
    body = {"name": f"SIM electronics high value {ctx.run_id}", "description": "sim rule", "ruleType": "JSON",
            "ruleJson": json.dumps(rule_json), "action": "HOLD", "score": 70, "priority": 5, "enabled": True,
            "category": "FRAUD"}
    r = maker.post("/rules", body)
    if r.status in (401, 403):
        sc.check(False, "PSP admin (maker) can create a PSP rule", r)
        maker = ctx.admin
        r = maker.post("/rules", body)
    sc.check(r.status in (200, 201), "create rule", r)
    rule = r.json or {}
    rid = rule.get("id")
    p["sim_rule_id"] = rid
    sc.check(rule.get("enabled") is False and str(rule.get("lifecycleStatus", "")).upper().startswith("PENDING"),
             "new rule is pending approval (maker/checker)", r)
    pend = maker.get("/rules/governance/pending")
    versions = [v for v in items(pend) if str(v.get("ruleId") or (v.get("rule") or {}).get("id")) == str(rid)] or items(pend)
    vr = maker.get(f"/rules/{rid}/versions")
    vlist = items(vr) or (vr.json if isinstance(vr.json, list) else [])
    vid = (vlist[-1] if vlist else {}).get("id") or (vlist[-1] if vlist else {}).get("versionId")
    sc.check(bool(vid), "rule version visible", vr)
    if not vid:
        return
    r = maker.post(f"/rules/governance/versions/{vid}/approve", {"reason": "self approve attempt"})
    sc.check(r.status == 403, "maker cannot approve own rule", r)
    checker = p["users"]["compliance"]["client"]
    r = checker.post(f"/rules/governance/versions/{vid}/approve", {"reason": "sim checker approval"})
    if r.status in (401, 403):
        sc.note(f"PSP compliance officer cannot approve PSP rule ({r.status}); using platform checker")
        r = ctx.platform_checker.post(f"/rules/governance/versions/{vid}/approve", {"reason": "sim checker approval"})
    sc.check(r.status in (200, 201, 204), "checker approves rule", r)
    rr = maker.get(f"/rules/{rid}")
    sc.check(rr.status == 200 and (rr.json or {}).get("enabled") is True, "rule enabled after approval", rr)
    time.sleep(2)
    hit = ingest(ctx, "card", txn_body(ctx, "card", RULE_AMOUNT + 50_000, mcc="5732"))
    j = hit.json or {}
    sc.check(j.get("action") in ("HOLD", "BLOCK"), "rule-matching txn is HOLD/BLOCK", hit)
    sc.check(any(str(rid) in str(x) or "SIM electronics" in str(x) for x in (j.get("reasons") or [])) or
             str(rid) in json.dumps(j.get("scores") or {}), "rule id/name cited in decision", hit, severity="WARN")
    miss = ingest(ctx, "card", txn_body(ctx, "card", RULE_AMOUNT - 100_000, mcc="5732"))
    sc.check((miss.json or {}).get("action") == "ALLOW", "below-threshold txn ALLOW", miss)
    r = maker.get(f"/rules/{rid}/effectiveness")
    sc.check(r.status == 200, "rule effectiveness endpoint", r, severity="WARN")
    r = maker.get("/rules?size=200")
    sc.check(r.status == 200 and any(x.get("id") == rid for x in items(r) or (r.json if isinstance(r.json, list) else [])),
             "rule listed", r)
    # Other PSPs must not see card's rule.
    r = ctx.psp("momo")["users"]["admin"]["client"].get(f"/rules/{rid}")
    sc.check(r.status in (403, 404), "other PSP cannot read card's rule", r)


@scenario("R2", "rules", "Rule disable / version history / rollback")
def r2(ctx, sc):
    p = ctx.psp("card")
    rid = p.get("sim_rule_id")
    if not rid:
        raise SkipScenario("R1 did not create a rule")
    maker = p["users"]["admin"]["client"]
    r = maker.post(f"/rules/{rid}/disable")
    sc.check(r.status in (200, 201, 202), "request rule disable", r)
    vr = maker.get(f"/rules/{rid}/versions")
    vl = items(vr) or (vr.json if isinstance(vr.json, list) else [])
    sc.check(len(vl) >= 2, "disable creates a new pending version", vr)
    sc.note(f"versions: {[(v.get('id'), v.get('status') or v.get('lifecycleStatus')) for v in vl]}")
    # leave the rule enabled for the edge scenarios: reject the disable
    pending = [v for v in vl if str(v.get("status") or v.get("lifecycleStatus")).upper() == "PENDING_APPROVAL"]
    active = [v for v in vl if str(v.get("status") or v.get("lifecycleStatus")).upper() == "ACTIVE"]
    if active:
        r = ctx.platform_checker.post(f"/rules/governance/versions/{active[0]['id']}/reject", {"reason": "not pending"})
        sc.check(r.status == 409, "rejecting a non-pending version -> 409 (not 500)", r)
    if pending:
        r = ctx.platform_checker.post(f"/rules/governance/versions/{pending[0]['id']}/reject", {"reason": "keep enabled for sim"})
        sc.check(r.status in (200, 201, 204), "checker rejects pending disable", r)
        rr = maker.get(f"/rules/{rid}")
        sc.check(rr.status == 200 and (rr.json or {}).get("enabled") is True, "rule still enabled after rejected disable", rr)


# ───────────────────────────────────────────── T: transactions / typologies ─────────────────────

@scenario("T1", "ingest", "Normal traffic at volume (all PSPs, concurrent)")
def t1(ctx, sc):
    n = ctx.args.volume
    results = []

    def one(i, key):
        amt = random.choice([1500, 2500, 4999, 7500, 12000, 20000, 35000])
        body = txn_body(ctx, key, amt, mcc=random.choice([m["mcc"] for m in ctx.psp(key)["merchants"]
                                                          if m["mcc"] not in ("7995", "6051")]))
        r = ingest(ctx, key, body)
        return key, r.status, (r.json or {}).get("action"), r.elapsed_ms, (r.json or {}).get("txnId")

    jobs = [(i, k) for k in ctx.psps for i in range(n)]
    t0 = time.time()
    with cf.ThreadPoolExecutor(max_workers=ctx.args.threads) as ex:
        for res in ex.map(lambda a: one(*a), jobs):
            results.append(res)
    wall = time.time() - t0
    lat = [x[3] for x in results if x[1] == 200]
    by_status = {}
    for x in results:
        by_status[x[1]] = by_status.get(x[1], 0) + 1
    by_action = {}
    for x in results:
        by_action[x[2]] = by_action.get(x[2], 0) + 1
    ctx.volume_txn_ids = [x[4] for x in results if x[4]]
    p50 = statistics.median(lat) if lat else None
    p95 = sorted(lat)[int(len(lat) * 0.95) - 1] if lat else None
    p99 = sorted(lat)[int(len(lat) * 0.99) - 1] if lat else None
    sc.note(f"{len(results)} txns in {wall:.1f}s ({len(results) / wall:.1f} tps) status={by_status} actions={by_action} "
            f"latency ms p50={p50 and round(p50)} p95={p95 and round(p95)} p99={p99 and round(p99)}")
    ok = by_status.get(200, 0)
    sc.check(ok >= 0.99 * len(results), f"≥99% of normal ingest succeed ({ok}/{len(results)})", {"byStatus": by_status})
    allow = by_action.get("ALLOW", 0)
    sc.check(allow >= 0.85 * ok, f"normal traffic mostly ALLOW ({allow}/{ok}) — false-positive rate", {"byAction": by_action})
    sc.check(p95 is not None and p95 < 2000, f"p95 ingest latency < 2000ms (p95={p95 and round(p95)})", {"p95": p95})


@scenario("T2", "ingest", "Idempotent ingest (Idempotency-Key / clientReference)")
def t2(ctx, sc):
    body = txn_body(ctx, "card", 3333)
    r1 = ingest(ctx, "card", body)
    r2 = ingest(ctx, "card", body)
    sc.check(r1.status == 200 and r2.status == 200, "both posts accepted", r2)
    sc.check((r1.json or {}).get("txnId") == (r2.json or {}).get("txnId"), "replay returns same txnId", r2)
    cnt = psql(f"select count(*) from transactions where client_reference='{body['clientReference']}'")
    sc.check(cnt == "1", f"exactly one stored transaction for clientReference (got {cnt})")
    bad = dict(body, merchantId="999999999", clientReference=body["clientReference"] + "-bad")
    r = ingest(ctx, "card", bad)
    sc.check(r.status in (400, 404), "unknown merchantId -> 4xx with error body", r)
    bad2 = dict(body, edgeAssessmentId="not-a-uuid", clientReference=body["clientReference"] + "-bad2")
    r = ingest(ctx, "card", bad2)
    sc.check(r.status == 400, "non-UUID edgeAssessmentId -> 400", r)
    bad3 = dict(body, amountCents=-5, clientReference=body["clientReference"] + "-bad3")
    r = ingest(ctx, "card", bad3)
    sc.check(r.status == 400, "negative amount -> 400 validation", r)
    r = ctx.psp("card")["users"]["admin"]["client"].post("/transactions/ingest/batch",
                                                         [txn_body(ctx, "card", 1100), txn_body(ctx, "card", 1200)])
    sc.check(r.status == 200 and isinstance(r.json, list) and len(r.json) == 2, "batch ingest of 2", r)


def typology(ctx, sc, key, label, bodies, expect_flag=True, final_only=True):
    resps = []
    for b in bodies:
        resps.append(ingest(ctx, key, b))
        time.sleep(0.05)
    for r in resps:
        remember(ctx, label, key, r)
    errs = [r for r in resps if r.status != 200]
    sc.check(not errs, f"{label}: all {len(resps)} ingests accepted", errs[0] if errs else None)
    targets = resps[-1:] if final_only else resps
    flagged_any = False
    detail = []
    for r in targets:
        f, action, alerts = flagged(ctx, key, r)
        flagged_any |= f
        detail.append({"txnId": (r.json or {}).get("txnId"), "action": action, "reasons": (r.json or {}).get("reasons"),
                       "alerts": [a.get("reason") or a.get("ruleName") or a.get("alertType") for a in alerts][:5]})
    if expect_flag:
        sc.check(flagged_any, f"{label}: detected (HOLD/BLOCK or alert raised)", {"final": detail})
    else:
        sc.check(not flagged_any, f"{label}: not flagged", {"final": detail})
    sc.note(f"{label}: {json.dumps(detail)[:500]}")
    return resps


@scenario("T3", "typology", "Structuring below CTR threshold")
def t3(ctx, sc):
    acct = f"ACC-STRUCT-{ctx.run_id}"
    pan = pan_for(acct)
    bodies = [txn_body(ctx, "card", amt, mcc="5411", pan=pan, customerAccountReference=acct, cashTransaction=True,
                       channelType="POS", currency="USD")
              for amt in (1_420_000, 1_390_000, 1_450_000, 1_410_000)]
    typology(ctx, sc, "card", "structuring", bodies)


@scenario("T4", "typology", "Rapid movement of funds (in then out)")
def t4(ctx, sc):
    acct = f"ACC-RAPID-{ctx.run_id}"
    bodies = [txn_body(ctx, "momo", 9_000_000, customerAccountReference=acct, direction="INBOUND", channelType="MOBILE"),
              txn_body(ctx, "momo", 8_900_000, customerAccountReference=acct, direction="OUTBOUND", channelType="MOBILE"),
              txn_body(ctx, "momo", 8_800_000, customerAccountReference=acct, direction="OUTBOUND", channelType="MOBILE")]
    typology(ctx, sc, "momo", "rapid-movement", bodies)


@scenario("T5", "typology", "Velocity bursts (1m / 5m windows)")
def t5(ctx, sc):
    pan = pan_for("velocity" + ctx.run_id)
    bodies = [txn_body(ctx, "card", 2000 + i, mcc="5411", pan=pan) for i in range(14)]
    resps = typology(ctx, sc, "card", "velocity-1m", bodies)
    actions = [(r.json or {}).get("action") for r in resps]
    first_flag = next((i for i, a in enumerate(actions) if a in ("HOLD", "BLOCK")), None)
    sc.note(f"velocity actions sequence: {actions}; first HOLD/BLOCK at #{first_flag}")
    reasons = " ".join(json.dumps((r.json or {}).get("reasons")) for r in resps[-3:])
    sc.check(re.search(r"(?i)veloc|1m|5m|burst|count", reasons) is not None, "velocity reason cited", {"reasons": reasons[:400]},
             severity="WARN")


@scenario("T6", "typology", "Z-score amount outlier")
def t6(ctx, sc):
    pan = pan_for("zscore" + ctx.run_id)
    acct = f"ACC-Z-{ctx.run_id}"
    base = [txn_body(ctx, "card", random.randint(1800, 2600), mcc="5411", pan=pan, customerAccountReference=acct) for _ in range(20)]
    for b in base:
        ingest(ctx, "card", b)
    time.sleep(1)
    outlier = txn_body(ctx, "card", 4_900_000, mcc="5411", pan=pan, customerAccountReference=acct)
    typology(ctx, sc, "card", "zscore-outlier", [outlier])


@scenario("T7", "typology", "High-risk MCC (gambling 7995, crypto 6051)")
def t7(ctx, sc):
    typology(ctx, sc, "card", "high-risk-mcc-7995", [txn_body(ctx, "card", 180_000, mcc="7995")])
    typology(ctx, sc, "vasp", "high-risk-mcc-6051", [txn_body(ctx, "vasp", 900_000, mcc="6051", channelType="CRYPTO")])


@scenario("T8", "typology", "Blacklist hits are tenant-scoped")
def t8(ctx, sc):
    ip = f"203.0.113.{random.randint(10, 250)}"
    term = f"T-BL-{ctx.run_id}"
    card = ctx.psp("card")
    # Fixtures go through the blacklist REST API as the card PSP's own admin (tenant-scoped write).
    ca = card["users"]["admin"]["client"]
    for etype, val in (("ip", ip), ("terminal", term)):
        r = ca.post("/blacklist", {"entryType": etype, "value": val, "reason": "sim fixture"})
        sc.check(r.status == 201 and str((r.json or {}).get("pspId")) == str(card["id"]),
                 f"PSP admin blacklists {etype} via REST (scoped to own PSP)", r)
    r = ca.post("/blacklist", {"entryType": "ip", "value": ip, "reason": "dup"})
    sc.check(r.status == 409, "duplicate active entry -> 409", r)
    r = ca.post("/blacklist", {"entryType": "ip", "value": ip + "9", "reason": "x", "platformWide": True,
                               "pspId": ctx.psp("momo")["id"]})
    j = r.json or {}
    sc.check(r.status in (400, 403) or (r.status == 201 and str(j.get("pspId")) == str(card["id"])
                                         and not j.get("platformWide")),
             "PSP admin cannot write platform-wide or another PSP's entry", r)
    r = ctx.psp("momo")["users"]["admin"]["client"].get("/blacklist?size=200")
    sc.check(r.status == 200 and not [e for e in items(r) if str(e.get("pspId")) == str(card["id"])],
             "other PSP cannot see card's blacklist entries", r)
    r = ingest(ctx, "card", txn_body(ctx, "card", 4000, mcc="5411", ipAddress=ip))
    sc.check((r.json or {}).get("action") in ("BLOCK", "HOLD"), "blacklisted IP -> BLOCK/HOLD", r)
    r = ingest(ctx, "card", txn_body(ctx, "card", 4000, mcc="5411", terminalId=term))
    sc.check((r.json or {}).get("action") in ("BLOCK", "HOLD"), "blacklisted terminal -> BLOCK/HOLD", r)
    r = ingest(ctx, "momo", txn_body(ctx, "momo", 4000, ipAddress=ip))
    sc.check((r.json or {}).get("action") == "ALLOW", "same IP at another PSP not blocked (tenant scope)", r)
    r = ctx.admin.get(f"/blacklist?pspId={card['id']}")
    sc.check(r.status == 200 and len(items(r)) >= 2, "platform admin lists the PSP's blacklist", r)


@scenario("T9", "typology", "Sanctions exact / fuzzy / PEP screening")
def t9(ctx, sc):
    c = ctx.psp("vasp")["users"]["admin"]["client"]
    r = c.post("/sanctions/screen", {"name": fixtures.SANCTIONED_EXACT, "entityType": "PERSON"})
    j = r.json or {}
    sc.check(r.status == 200 and (j.get("matchFound") or j.get("match")), "exact sanctioned name matches", r)
    sc.check(not PROVIDER_LEAK.search(r.text), "screening response does not name an AI provider", r)
    r = c.post("/sanctions/screen", {"name": fixtures.SANCTIONED_FUZZY, "entityType": "PERSON"})
    j = r.json or {}
    sc.check(r.status == 200 and (j.get("matchFound") or j.get("match") or j.get("matchCount", 0) > 0), "fuzzy variant matches", r)
    r = c.post("/sanctions/screen", {"name": fixtures.SANCTIONED_ORG, "entityType": "ORGANIZATION"})
    j = r.json or {}
    sc.check(r.status == 200 and (j.get("matchFound") or j.get("match")), "sanctioned organisation matches", r)
    r = c.post("/sanctions/screen", {"name": fixtures.PEP_NAME, "entityType": "PERSON"})
    j = r.json or {}
    sc.check(r.status == 200 and (j.get("pepMatchFound") or any(m.get("isPep") or m.get("category") == "PEP" for m in j.get("matches") or [])),
             "PEP flagged as PEP (not sanctions)", r)
    clean_hits = []
    for n in fixtures.CLEAN_NAMES:
        r = c.post("/sanctions/screen", {"name": n, "entityType": "PERSON"})
        if (r.json or {}).get("matchFound") or (r.json or {}).get("match"):
            clean_hits.append(n)
    sc.check(not clean_hits, f"clean names do not match ({len(fixtures.CLEAN_NAMES)} tried)", {"falsePositives": clean_hits})
    # Merchant onboarding screening with a sanctioned entity
    p = ctx.psp("vasp")
    r = ctx.admin.post("/merchants/onboard", {"pspId": p["id"], "legalName": fixtures.SANCTIONED_ORG, "country": "KEN",
                                              "registrationNumber": f"SANC-{ctx.run_id}", "mcc": "6051",
                                              "contactEmail": "x@sanc.test",
                                              "beneficialOwners": [{"fullName": fixtures.SANCTIONED_EXACT, "dateOfBirth": "1971-03-04",
                                                                    "nationality": "IRN", "passportNumber": "P-SIM-0001",
                                                                    "ownershipPercentage": 60}]})
    j = r.json or {}
    sc.check(j.get("decision") in ("REJECT", "REVIEW") or r.status in (202, 403),
             "onboarding a sanctioned merchant is REJECT/REVIEW", r)
    r = ctx.admin.post("/merchants/onboard", {"pspId": p["id"], "legalName": f"Clean Trading {ctx.run_id}", "country": "KEN",
                                              "registrationNumber": f"CLEAN-{ctx.run_id}", "mcc": "5411", "contactEmail": "c@clean.test",
                                              "beneficialOwners": [{"fullName": "Wanjiru Kamau-Otieno", "dateOfBirth": "1984-05-12",
                                                                    "nationality": "KEN", "nationalId": f"KE-{ctx.run_id}",
                                                                    "ownershipPercentage": 100}]})
    sc.check(r.status in (200, 201, 202), "onboarding a clean merchant succeeds", r)


@scenario("T10", "typology", "Tipping-off: PSP-facing payloads carry no filing language")
def t10(ctx, sc):
    leaks = []
    for who, resp in ctx.psp_responses:
        if "/transactions/ingest" in resp.url and TIPPING_OFF.search(resp.text or ""):
            leaks.append({"who": who, "url": resp.url, "body": resp.text[:300]})
    sc.check(not leaks, f"no ingest decision to a PSP contains SAR/STR/filing language ({len(leaks)} hits)", {"samples": leaks[:3]})
    u = ctx.psp("card")["users"]["user"]["client"]
    r = u.get("/compliance/sar?size=5")
    sc.check(r.status in (401, 403), "PSP_USER (operations) cannot list SARs", r)


# ───────────────────────────────────────────── L: alerts / cases / SAR / reports ────────────────

def keyset_walk(sc, client, path, label, pages=6, size=10):
    seen, dupes, cursor, n = set(), 0, None, 0
    first = None
    for _ in range(pages):
        url = f"{path}{'&' if '?' in path else '?'}size={size}" + (f"&cursor={cursor}" if cursor else "")
        r = client.get(url)
        if first is None:
            first = r
        if r.status != 200:
            sc.check(False, f"{label}: page fetch", r)
            return
        j = r.json or {}
        for it in items(r):
            k = it.get("id") or it.get("alertId") or it.get("txnId")
            if k in seen:
                dupes += 1
            seen.add(k)
        n += 1
        cursor = j.get("nextCursor")
        if not j.get("hasNext") or not cursor:
            break
    # nextCursor is omitted (non_null JSON) on the last page, so the shape check is on hasNext.
    sc.check("hasNext" in (first.json or {}) if first else False, f"{label}: keyset response shape (hasNext/nextCursor)", first)
    sc.check(dupes == 0, f"{label}: no duplicates across {n} keyset pages ({len(seen)} rows)", {"dupes": dupes})


@scenario("L1", "alerts", "Alerts list, keyset pagination, prioritisation, resolve")
def l1(ctx, sc):
    p = ctx.psp("card")
    c = p["users"]["compliance"]["client"]
    r = c.get("/alerts?size=20")
    if r.status == 403:
        sc.check(False, "PSP compliance officer can list alerts", r)
        c = p["users"]["admin"]["client"]
        r = c.get("/alerts?size=20")
    al = items(r)
    sc.check(r.status == 200 and len(al) > 0, f"alerts exist for card PSP ({len(al)})", r)
    keyset_walk(sc, c, "/alerts", "alerts", size=5)
    keyset_walk(sc, p["users"]["admin"]["client"], "/transactions", "transactions", size=25)
    r = c.get("/alerts?sort=priority&size=20")
    sc.check(r.status == 200 and items(r), "priority-sorted alerts", r)
    pr = [a.get("priorityScore") or a.get("priority") for a in items(r)]
    sc.note(f"priority order sample: {pr[:10]}")
    r = c.get("/alerts/count/active")
    sc.check(r.status == 200, "active alert count", r)
    if al:
        aid = al[0].get("id") or al[0].get("alertId")
        r = c.get(f"/alerts/{aid}")
        sc.check(r.status == 200, "get alert", r)
        r = c.put(f"/alerts/{aid}/resolve", {"disposition": "FALSE_POSITIVE", "notes": "sim: verified benign"})
        sc.check(r.status in (200, 202), "resolve alert with disposition", r)
        ctx.resolved_alert = aid
        r = c.get("/alerts/disposition-stats?days=7")
        sc.check(r.status == 200, "disposition stats", r)
        r = ctx.psp("momo")["users"]["admin"]["client"].get(f"/alerts/{aid}")
        sc.check(r.status in (403, 404), "other PSP cannot read alert", r)


@scenario("L2", "cases", "Case management: create, assign, escalate, decide, timeline")
def l2(ctx, sc):
    p = ctx.psp("card")
    co = p["users"]["compliance"]
    c = co["client"]
    r = c.get("/compliance/cases?size=50")
    sc.check(r.status == 200, "compliance officer lists cases", r)
    auto = items(r)
    sc.note(f"auto-created cases visible to card compliance: {len(auto)}")
    r = c.post("/compliance/cases/workflow/create", {"caseReference": f"SIM-CASE-{ctx.run_id}", "description": "Sim structuring case",
                                                     "priority": "HIGH", "creatorUserId": co["id"]})
    sc.check(r.status in (200, 201), "create case", r)
    case = r.json or {}
    cid = case.get("id") or case.get("caseId")
    if not cid:
        return
    ctx.case_id = cid
    sc.check(str(case.get("pspId") or (case.get("psp") or {}).get("id") or psql(f"select psp_id from compliance_cases where id={cid}")) == str(p["id"]),
             "case is owned by creator's PSP", {"case": str(case)[:300]})
    r = c.post("/compliance/cases/workflow/assign", {"caseId": cid, "assigneeUserId": p["users"]["analyst"]["id"], "assignerUserId": co["id"]})
    sc.check(r.status in (200, 201), "assign case", r)
    r = c.post(f"/cases/{cid}/assign", {"assignTo": co["username"]})
    sc.check(r.status in (200, 201), "assign case to self via /cases/{id}/assign", r)
    r = c.post(f"/cases/{cid}/escalate", {"reason": "sim escalation via /cases"})
    sc.check(r.status in (200, 201), "POST /cases/{id}/escalate", r)
    r = c.post("/compliance/cases/workflow/escalate", {"caseId": cid, "escalatedToUserId": co["id"], "reason": "sim", "userId": co["id"]})
    sc.check(r.status in (200, 201), "workflow escalate", r)
    r = c.post(f"/cases/{cid}/decisions", {"decisionType": "FILE_SAR", "justification": "Structuring pattern confirmed by sim analyst"})
    sc.check(r.status in (200, 201, 202), "case decision FILE_SAR", r)
    r = c.get(f"/cases/{cid}/timeline")
    sc.check(r.status == 200 and (items(r) or r.json), "case timeline", r)
    r = c.get(f"/compliance/cases/{cid}")
    sc.check(r.status == 200, "get case", r)
    sc.note(f"case status after decision: {(r.json or {}).get('status')}")
    r = ctx.psp("momo")["users"]["compliance"]["client"].get(f"/compliance/cases/{cid}")
    sc.check(r.status in (403, 404), "other PSP cannot read case", r)
    r = c.get(f"/cases/{cid}/sla")
    sc.check(r.status == 200, "case SLA", r, severity="WARN")


@scenario("L3", "reporting", "SAR workflow, XML export, CTR/LCTR CSV+PDF, scheme pack, reports, exports")
def l3(ctx, sc):
    p = ctx.psp("card")
    co = p["users"]["compliance"]
    c = co["client"]
    m = merchant(ctx, "card", "5411")
    r = c.post("/compliance/sar", {"merchantId": m["id"], "caseId": getattr(ctx, "case_id", None), "priority": "HIGH",
                                   "narrative": "Sim: repeated cash deposits just under the reporting threshold."})
    sc.check(r.status in (200, 201), "create SAR", r)
    sid = (r.json or {}).get("id")
    if sid:
        r = c.get(f"/compliance/sar/{sid}/xml")
        sc.check(r.status == 200 and b"<" in r.raw[:50], "SAR XML export", r)
    r = c.post("/compliance/sar/workflow/create", {"sarReference": f"SIM-SAR-{ctx.run_id}", "narrative": "Sim structuring narrative " * 3,
                                                  "suspiciousActivityType": "STRUCTURING", "jurisdiction": "KE", "sarType": "INITIAL",
                                                  "creatorUserId": co["id"], "suspicionAroseAt": dt.datetime.now().replace(microsecond=0).isoformat()})
    sc.check(r.status in (200, 201), "SAR workflow create", r)
    wid = (r.json or {}).get("id") or (r.json or {}).get("sarId")
    if wid:
        r = c.post("/compliance/sar/workflow/submit", {"sarId": wid, "userId": co["id"]})
        sc.check(r.status in (200, 201), "SAR submit", r)
        r = c.post("/compliance/sar/workflow/approve", {"sarId": wid, "userId": co["id"]})
        sc.check(r.status in (400, 403, 409), "SAR maker cannot approve own SAR", r)
        chk = ctx.platform_checker
        r = chk.post("/compliance/sar/workflow/approve", {"sarId": wid, "userId": None})
        sc.check(r.status in (200, 201), "SAR approved by a different user", r)
        r = chk.post("/compliance/sar/workflow/file", {"sarId": wid, "userId": None, "filingReference": f"FRC-{ctx.run_id}"})
        sc.check(r.status in (200, 201), "SAR filed", r)
    start = (dt.datetime.now() - dt.timedelta(days=1)).replace(microsecond=0).isoformat()
    end = (dt.datetime.now() + dt.timedelta(hours=1)).replace(microsecond=0).isoformat()
    a = p["users"]["admin"]["client"]
    r = a.get(f"/reporting/regulatory/ctr?startDate={start}&endDate={end}")
    sc.check(r.status == 200, "CTR report", r)
    ctr_rows = items(r) or ((r.json or {}).get("transactions") if isinstance(r.json, dict) else None) or []
    sc.note(f"CTR rows: {len(ctr_rows) if isinstance(ctr_rows, list) else ctr_rows}")
    for fmt, magic in (("csv", None), ("pdf", b"%PDF")):
        r = a.get(f"/reporting/regulatory/ctr/export?format={fmt}&startDate={start}&endDate={end}")
        sc.check(r.status == 200 and (magic is None or r.raw[:4] == magic) and len(r.raw) > 0, f"CTR export {fmt}", r)
    r = a.get(f"/reporting/regulatory/lctr/export?format=csv&startDate={start}&endDate={end}")
    sc.check(r.status == 200, "LCTR export csv", r)
    today = dt.date.today()
    for fmt, magic in (("JSON", b"{"), ("CSV", None), ("PDF", b"%PDF")):
        r = a.get(f"/reporting/schemes/pack?type=SCHEME_MONITORING&startDate={today - dt.timedelta(days=1)}&endDate={today}&format={fmt}")
        sc.check(r.status == 200 and (magic is None or r.raw[:len(magic)] == magic), f"scheme pack {fmt}", r)
        if fmt == "CSV":
            ct = r.header("Content-Type") or ""
            sc.check("csv" in ct or "text" in ct, "scheme pack CSV content type", r)
    # ReportGenerateRequest.dateFrom/dateTo are LocalDateTime (ISO date-time), like the Console sends.
    r = a.post("/reports/generate", {"reportType": "TXN_001", "outputFormat": "PDF", "dateFrom": f"{today - dt.timedelta(days=1)}T00:00:00",
                                     "dateTo": f"{today}T23:59:59", "parameters": {}})
    sc.check(r.status in (200, 201, 202), "report engine generate TXN_001", r)
    ex = (r.json or {}).get("executionId")
    if ex:
        st = wait_until(lambda: (a.get(f"/reports/status/{ex}").json or {}).get("status") in ("COMPLETED", "FAILED", "SUCCESS") and
                        a.get(f"/reports/status/{ex}"), timeout=40, interval=2)
        sc.check(bool(st) and (st.json or {}).get("status") in ("COMPLETED", "SUCCESS"), "report execution completes", st)
    r = a.post("/console/exports", {"viewType": "TRANSACTIONS", "format": "CSV", "filters": {}})
    sc.check(r.status == 200 and b"," in r.raw[:500], "console CSV export (sync)", r)
    r = a.post("/console/exports", {"viewType": "ALERTS", "format": "PDF", "filters": {}})
    if r.status == 202:
        job = (r.json or {}).get("jobUuid")
        fin = wait_until(lambda: (a.get(f"/console/exports/jobs/{job}").json or {}).get("status") in ("COMPLETED", "FAILED") and
                         a.get(f"/console/exports/jobs/{job}"), timeout=60, interval=2)
        sc.check(bool(fin) and (fin.json or {}).get("status") == "COMPLETED", "async export job completes", fin)
        d = a.get(f"/console/exports/jobs/{job}/download")
        sc.check(d.status == 200 and d.raw[:4] == b"%PDF", "async export download is PDF", d)
    else:
        sc.check(r.status == 200 and r.raw[:4] == b"%PDF", "console PDF export", r)
    r = a.post("/console/exports", {"viewType": "TRANSACTIONS", "format": "XLSX", "filters": {}})
    sc.check(r.status in (200, 202), "console XLSX export", r)
    for path in ("/exports/cases.csv", "/exports/sars.csv"):
        r = c.get(path)
        sc.check(r.status == 200, f"GET {path}", r)
        if r.status == 200 and str(ctx.psp("momo")["code"]) in r.text:
            sc.check(False, f"{path} leaks another PSP's rows", r)


# ───────────────────────────────────────────── AI ───────────────────────────────────────────────

@scenario("AI1", "ai", "AI decision layer degrades gracefully with no LAYA key (shadow)")
def ai1(ctx, sc):
    r = ctx.admin.get("/ai/status")
    j = r.json or {}
    sc.check(r.status == 200 and j.get("configured") is False, "AI status: not configured", r)
    sc.check(j.get("shadowMode") is True and j.get("active") in (False, None), "shadow mode on, AI inactive", r)
    leaks = [(w, x.url, x.text[:200]) for w, x in ctx.psp_responses if PROVIDER_LEAK.search(x.text or "")]
    sc.check(not leaks, f"no PSP-facing response names the AI provider ({len(ctx.psp_responses)} responses scanned)",
             {"samples": leaks[:3]})
    txid = (getattr(ctx, "volume_txn_ids", None) or [None])[0]
    if txid:
        r = ctx.admin.get(f"/ai/audit/transaction/{txid}")
        sc.check(r.status in (200, 404), "AI audit lookup for txn does not error", r)


# ───────────────────────────────────────────── E: Edge Node ─────────────────────────────────────

def edge_ssl_context(pki, edge_id=None):
    ctxs = ssl.create_default_context(cafile=pki["ca"])
    ctxs.minimum_version = ssl.TLSVersion.TLSv1_3
    if edge_id:
        d = os.path.dirname(pki["ca"])
        ctxs.load_cert_chain(os.path.join(d, f"edge-client-{edge_id}.crt"), os.path.join(d, f"edge-client-{edge_id}.key"))
    return ctxs


def launch_edge(ctx, key, node_view, code, edge_id):
    import gen_certs
    gen_certs.base()
    pki = gen_certs.edge(edge_id)
    work = os.path.join(HERE, "results", "edge", edge_id)
    os.makedirs(work, exist_ok=True)
    port = ctx.args.edge_port
    env = dict(os.environ)
    env.update({
        "EDGE_BIND_ADDRESS": "127.0.0.1", "EDGE_PORT": str(port),
        "EDGE_TLS_KEYSTORE": pki["tls_p12"], "EDGE_TLS_KEYSTORE_PASSWORD": pki["password"], "EDGE_TLS_KEY_ALIAS": "edge",
        "CONTROLPLANE_URL": ctx.proxy, "EDGE_EDGEID": edge_id, "EDGE_PSPID": str(ctx.psp(key)["id"]),
        "EDGE_ENROLLMENT_CODE": code,
        "CONTROLPLANE_ED25519_PUBLIC_KEY": ctx.secrets["EDGE_BUNDLE_SIGNING_PUBLIC_KEY"],
        "CONTROLPLANE_X25519_PUBLIC_KEY": ctx.secrets["EDGE_METRICS_INGEST_PUBLIC_KEY"],
        "EDGE_MTLS_KEYSTORE": pki["client_p12"], "EDGE_MTLS_KEYSTORE_PASSWORD": pki["password"],
        "EDGE_MTLS_TRUSTSTORE": pki["truststore"], "EDGE_MTLS_TRUSTSTORE_PASSWORD": pki["password"],
        "EDGE_IDENTITY_FILE": os.path.join(work, "edge-identity.json"),
        "FEATURESTORE_HOST": "127.0.0.1", "FEATURESTORE_PORT": os.environ.get("SIM_AEROSPIKE_PORT", "53000"), "FEATURESTORE_NAMESPACE": "hokeka",
        "EDGE_RETENTION_DAYS": "90", "EDGE_BUNDLE_POLL_INTERVAL": "5s", "EDGE_METRICS_INTERVAL": "10s",
    })
    jar = os.path.join(ROOT, "edge-host", "target", "edge-host-0.1.0.jar")
    lib = ctx.args.edge_native_dir
    logf = open(os.path.join(HERE, "results", "logs", f"edge-{edge_id}.log"), "w", encoding="utf-8")
    proc = subprocess.Popen(["java", "-Xmx" + os.environ.get("EDGE_XMX", "384m"), f"-Djava.library.path={lib}", "-jar", jar], env=env, stdout=logf,
                            stderr=subprocess.STDOUT, cwd=work)
    ctx.edge.update({"proc": proc, "edge_id": edge_id, "pki": pki, "port": port, "key": key, "log": logf.name,
                     "base": f"https://127.0.0.1:{port}"})
    with open(os.path.join(HERE, "results", "edge.pid"), "w") as fh:
        fh.write(str(proc.pid))
    return proc


def edge_client(ctx):
    insecure = ssl.create_default_context()
    insecure.check_hostname = False
    insecure.verify_mode = ssl.CERT_NONE
    return ctx.client("edge-local", base=ctx.edge["base"], ssl_context=insecure)


def edge_log(ctx):
    try:
        with open(ctx.edge["log"], encoding="utf-8", errors="replace") as fh:
            return fh.read()
    except Exception:
        return ""


def proxy_log():
    try:
        with open(os.path.join(HERE, "results", "logs", "mtls-proxy.log"), encoding="utf-8", errors="replace") as fh:
            return fh.read()
    except Exception:
        return ""


@scenario("E1", "edge", "Edge node request / approve / enrollment (real Edge Host)")
def e1(ctx, sc):
    if ctx.args.no_edge:
        raise SkipScenario("--no-edge")
    key = "card"
    p = ctx.psp(key)
    pa = p["users"]["admin"]["client"]
    r = pa.post("/edge/nodes", {"pspId": p["id"], "displayName": f"sim-edge-{ctx.run_id}"})
    sc.check(r.status == 201 and (r.json or {}).get("enrollmentCode"), "PSP admin requests edge node (code shown once)", r)
    j = r.json or {}
    node = j.get("node") or {}
    nid, code = node.get("id"), j.get("enrollmentCode")
    if not nid:
        return
    ctx.edge["node_id"] = nid
    r = pa.post(f"/edge/nodes/{nid}/approve")
    sc.check(r.status in (401, 403), "PSP admin cannot self-approve edge node", r)
    r = ctx.admin.post(f"/edge/nodes/{nid}/approve")
    sc.check(r.status == 200 and (r.json or {}).get("status") == "APPROVED", "platform admin approves node", r)
    edge_id = f"sim-{key}-{ctx.run_id}"
    proc = launch_edge(ctx, key, node, code, edge_id)
    ec = edge_client(ctx)
    up = wait_until(lambda: proc.poll() is None and ec.get("/actuator/health").status == 200, timeout=90, interval=2)
    sc.check(bool(up), "edge-host starts and serves TLS health", {"logTail": edge_log(ctx)[-1500:]})
    if proc.poll() is not None:
        sc.check(False, "edge-host process alive", {"exit": proc.returncode, "logTail": edge_log(ctx)[-2500:]})
        return
    # GET /edge/nodes/{id} answers {"node": {...}, "recentMetrics": [...]}.
    active = wait_until(lambda: ((ctx.admin.get(f"/edge/nodes/{nid}").json or {}).get("node") or {}).get("status") == "ACTIVE",
                        timeout=90, interval=3)
    nv = ctx.admin.get(f"/edge/nodes/{nid}")
    sc.check(bool(active), "node ACTIVE after enrollment", nv)
    sc.check("/api/v1/edge/enroll -> 202" in proxy_log(), "enroll call went through mTLS front end (202)", {"proxy": proxy_log()[-800:]})
    r = ctx.client("enroll-replay").post("/edge/enroll", {"edgeId": edge_id + "-x", "pspId": str(p["id"]), "enrollmentCode": code,
                                                         "x25519PublicKey": "AAAA", "ed25519PublicKey": "AAAA"})
    sc.check(r.status in (400, 403, 409), "enrollment code is single-use", r)


@scenario("E2", "edge", "Rule bundle pull + signature verification + evaluation")
def e2(ctx, sc):
    if ctx.args.no_edge or "proc" not in ctx.edge:
        raise SkipScenario("edge not running")
    ec = edge_client(ctx)

    def loaded():
        r = ec.get("/edge/status")
        j = r.json or {}
        # /edge/status reports ruleBundleVersion (-1 until a verified bundle is armed).
        v = j.get("ruleBundleVersion", j.get("bundleVersion"))
        return j if (isinstance(v, int) and v > 0) else None
    st = wait_until(loaded, timeout=60, interval=3)
    r = ec.get("/edge/status")
    sc.note(f"/edge/status: {r.text[:400]}")
    sc.check(bool(st), "edge loaded a verified bundle", r)
    sc.check((r.json or {}).get("nativeCore") in (True, "true", "loaded"), "native Rust core loaded (not Java fallback)", r)
    pl = proxy_log()
    bundle_lines = [l for l in pl.splitlines() if "/edge/bundle" in l]
    sc.note("bundle polls: " + " | ".join(bundle_lines[-4:]))
    sc.check(any("-> 200" in l for l in bundle_lines), "bundle served 200 to edge", {"lines": bundle_lines[-5:]})
    sc.check(any("-> 304" in l for l in bundle_lines), "subsequent polls are 304 (ETag)", {"lines": bundle_lines[-5:]}, severity="WARN")
    lg = edge_log(ctx)
    sc.check(not re.search(r"(?i)signature (invalid|verification failed)|bad signature", lg), "no signature failures in edge log",
             {"tail": lg[-800:]})
    mid = str(merchant(ctx, "card", "5732")["id"])
    base = {"merchant_id": mid, "currency": "USD", "country_code": "KE", "mcc": "5732"}
    r = ec.post("/edge/evaluate", dict(base, pan_hash="ph-" + uuid.uuid4().hex, txn_id="e-" + uuid.uuid4().hex, amount_cents=1500))
    sc.check(r.status == 200 and (r.json or {}).get("action") == "ALLOW", "normal txn ALLOW at edge", r)
    r = ec.post("/edge/evaluate", dict(base, pan_hash="ph-" + uuid.uuid4().hex, txn_id="e-" + uuid.uuid4().hex,
                                       amount_cents=RULE_AMOUNT + 90_000, amount=RULE_AMOUNT + 90_000))
    j = r.json or {}
    sc.check(r.status == 200 and j.get("action") in ("HOLD", "BLOCK"), "PSP rule (amount>X & mcc 5732) enforced at edge", r)
    sc.check("edgeAssessmentId" in j or "assessmentId" in j, "edge returns an assessment id for dual-post correlation", r)
    ctx.edge["last_eval"] = j
    # velocity from local feature store
    ph = "ph-vel-" + uuid.uuid4().hex
    acts = []
    for i in range(12):
        rr = ec.post("/edge/evaluate", dict(base, pan_hash=ph, txn_id=f"v{i}-" + uuid.uuid4().hex, amount_cents=1000 + i))
        acts.append((rr.json or {}).get("action"))
    sc.note(f"edge velocity sequence: {acts}")
    rr = ec.post("/edge/evaluate", dict(base, pan_hash=ph, txn_id="probe-" + uuid.uuid4().hex, amount_cents=999))
    sc.note(f"edge derived features after burst: {rr.text[:300]}")
    t0 = time.perf_counter()
    for i in range(50):
        ec.post("/edge/evaluate", dict(base, pan_hash="ph-l-" + str(i), txn_id="l-" + uuid.uuid4().hex, amount_cents=1200))
    per = (time.perf_counter() - t0) * 1000 / 50
    sc.note(f"edge sequential evaluate mean latency {per:.1f} ms")
    sc.check(per < 100, f"edge evaluate mean latency < 100ms ({per:.1f})")


@scenario("E3", "edge", "Edge metrics shipping (HSE-1) + fleet analytics")
def e3(ctx, sc):
    if ctx.args.no_edge or "proc" not in ctx.edge:
        raise SkipScenario("edge not running")
    ok = wait_until(lambda: "/api/v1/edge/metrics -> 204" in proxy_log(), timeout=45, interval=3)
    lines = [l for l in proxy_log().splitlines() if "/edge/metrics" in l]
    sc.check(bool(ok), "edge POSTed sealed metrics, CP answered 204", {"lines": lines[-5:], "edgeLog": edge_log(ctx)[-600:]})
    p = ctx.psp(ctx.edge["key"])
    r = p["users"]["admin"]["client"].get(f"/edge/nodes/analytics?pspId={p['id']}&days=1")
    sc.check(r.status == 200, "edge analytics endpoint", r)
    sc.note(f"analytics: {r.text[:400]}")
    r = p["users"]["admin"]["client"].get(f"/edge/nodes?pspId={p['id']}")
    nodes = items(r) or (r.json if isinstance(r.json, list) else [])
    mine = [n for n in nodes if n.get("edgeId") == ctx.edge["edge_id"]]
    sc.check(mine and mine[0].get("health") in ("HEALTHY", "OK", "UP") and mine[0].get("lastBundleVersion") is not None,
             "fleet list shows node healthy with bundle version", r)
    dec = [l for l in proxy_log().splitlines() if "/edge/decision" in l]
    sc.check(not any("-> 404" in l for l in dec), "edge AI decision client hits a valid CP path", {"lines": dec[-3:]})
    sc.check(not re.search(r"POST /edge/decision ", proxy_log()), "edge AI client uses /api/v1/edge/decision",
             {"lines": dec[-3:]}, severity="WARN")


@scenario("E4", "dual-post", "Edge pre-auth + cloud ingest correlated by edgeAssessmentId")
def e4(ctx, sc):
    if ctx.args.no_edge or "proc" not in ctx.edge:
        raise SkipScenario("edge not running")
    key = ctx.edge["key"]
    p = ctx.psp(key)
    ec = edge_client(ctx)
    m = merchant(ctx, key, "5732")
    txn_ref = f"{ctx.run_id}-dp-{uuid.uuid4().hex[:8]}"
    ev = ec.post("/edge/evaluate", {"pan_hash": "ph-dp-" + txn_ref, "txn_id": txn_ref, "amount_cents": RULE_AMOUNT + 70_000,
                                    "merchant_id": str(m["id"]), "currency": "USD", "mcc": "5732"})
    ej = ev.json or {}
    sc.check(ev.status == 200, "edge evaluate", ev)
    eaid = ej.get("edgeAssessmentId")
    sc.check(bool(eaid) and re.match(r"^[0-9a-f-]{36}$", str(eaid)) is not None, "edge minted a UUID edgeAssessmentId", ev)
    if not eaid:
        return
    # The Edge Host ships the transaction-free decision envelope itself (EdgeAssessmentShipper, mTLS).
    shipped = wait_until(lambda: psql(f"select count(*) from assessments where edge_assessment_id='{eaid}'") == "1",
                         timeout=40, interval=2)
    lines = [l for l in proxy_log().splitlines() if "/edge/assessments" in l]
    sc.check(bool(shipped), "edge shipped its assessment envelope to the CP over mTLS",
             {"proxy": lines[-4:], "edgeLog": edge_log(ctx)[-600:]})
    sc.check(any("-> 20" in l for l in lines), "CP accepted /api/v1/edge/assessments (2xx)", {"proxy": lines[-4:]})
    mt = ctx.client("edge-mtls", base=ctx.proxy + "/api/v1", ssl_context=edge_ssl_context(ctx.edge["pki"], ctx.edge["edge_id"]))
    mt.forwarded_https = False
    body = txn_body(ctx, key, RULE_AMOUNT + 70_000, mcc="5732", clientReference=txn_ref, edgeAssessmentId=eaid)
    ig = ingest(ctx, key, body)
    ij = ig.json or {}
    sc.check(ig.status == 200, "cloud ingest accepted", ig)
    sc.check(str(ij.get("edgeAssessmentId") or (ij.get("scores") or {}).get("edgeAssessmentId")) == eaid,
             "ingest response echoes edgeAssessmentId", ig)
    sc.check(ij.get("action") == ej.get("action"), f"cloud adopts edge decision ({ej.get('action')} vs {ij.get('action')})", ig)
    linked = psql(f"select count(*) from assessments where edge_assessment_id='{eaid}'")
    sc.check(linked == "1", "exactly one assessment row linked to edge id", {"rows": linked})
    ig2 = ingest(ctx, key, body)
    sc.check((ig2.json or {}).get("txnId") == ij.get("txnId"), "re-post is idempotent", ig2)
    other = txn_body(ctx, "momo", 5000, edgeAssessmentId=eaid, clientReference=txn_ref + "-x")
    r = ingest(ctx, "momo", other)
    sc.check(r.status == 409, "another PSP cannot adopt this edge assessment (409)", r)
    noc = ctx.client("edge-nocert", base=ctx.proxy + "/api/v1", ssl_context=edge_ssl_context(ctx.edge["pki"]))
    noc.forwarded_https = False
    r = noc.get("/edge/bundle")
    sc.check(r.status in (401, 403), "bundle refused without client cert", r)
    r = noc.get("/edge/bundle", headers={"X-SSL-Client-Verify": "SUCCESS", "X-SSL-Client-S-DN": f"CN={ctx.edge['edge_id']}"})
    sc.check(r.status in (401, 403), "forged X-SSL-Client-* headers are stripped by the front end", r)
    r = mt.post("/edge/decision", {"engine": "FRAUD_SCORING", "baselineDecision": "HOLD", "features": {"amount_cents": 1}, "async": False},
                headers={"X-Hokeka-Edge-Id": ctx.edge["edge_id"]})
    dj = r.json or {}
    sc.check(r.status == 200 and dj.get("finalDecision") == "HOLD" and (dj.get("fallback") or not dj.get("aiApplied")),
             "edge AI decision falls back to baseline with no AI key", r)
    sc.check(not PROVIDER_LEAK.search(r.text), "edge decision response does not name the AI provider", r)


@scenario("E5", "edge", "Edge fail-closed + suspend/revoke")
def e5(ctx, sc):
    if ctx.args.no_edge or "proc" not in ctx.edge:
        raise SkipScenario("edge not running")
    nid = ctx.edge.get("node_id")
    r = ctx.admin.post(f"/edge/nodes/{nid}/suspend")
    sc.check(r.status == 200 and (r.json or {}).get("status") == "SUSPENDED", "suspend node", r)
    ec = edge_client(ctx)
    gate = wait_until(lambda: (ec.post("/edge/evaluate", {"pan_hash": "ph-s", "txn_id": "s-" + uuid.uuid4().hex, "amount_cents": 100,
                                                          "merchant_id": "1"}).json or {}).get("action") == "HOLD", timeout=40, interval=4)
    sc.check(bool(gate), "suspended edge fails closed (HOLD) after next poll", {"edgeLog": edge_log(ctx)[-600:]})
    r = ctx.admin.post(f"/edge/nodes/{nid}/revoke")
    sc.check(r.status == 200 and (r.json or {}).get("status") == "REVOKED", "revoke node", r)


# ───────────────────────────────────────────── main ─────────────────────────────────────────────

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:%s/api/v1" % os.environ.get("SIM_CP_PORT", "2637"))
    ap.add_argument("--proxy", default="https://localhost:%s" % os.environ.get("SIM_PROXY_PORT", "8444"))
    ap.add_argument("--run-id")
    ap.add_argument("--only", nargs="*")
    ap.add_argument("--no-edge", action="store_true")
    ap.add_argument("--volume", type=int, default=200)
    ap.add_argument("--threads", type=int, default=16)
    ap.add_argument("--edge-port", type=int, default=18443)
    ap.add_argument("--edge-native-dir", default=os.path.join(ROOT, "edge-engine", "target-gnu", "release"))
    ap.add_argument("--keep-edge", action="store_true", help="leave the Edge Host running after the run")
    args = ap.parse_args()
    ctx = Ctx(args)
    print(f"run id {ctx.run_id}; CP {ctx.base}; results -> {ctx.out_dir}")
    seed_sc = seed(ctx)
    print(f"[S0] {seed_sc.status} ({sum(c['ok'] for c in seed_sc.checks)}/{len(seed_sc.checks)})")
    # Order matters: rules before edge (bundle must contain the rule); volume before alerts.
    order = ["A", "B", "C1", "D", "R", "T", "C2", "C3", "L", "AI", "E"]
    ordered = []
    for prefix in order:
        ordered += [x for x in runner.registry if re.match(rf"^{prefix}\d*$", x[0]) and x not in ordered]
    ordered += [x for x in runner.registry if x not in ordered]
    runner.registry = ordered
    scs = [seed_sc] + runner.run(ctx, only=args.only)
    if "proc" in ctx.edge and not args.keep_edge:
        ctx.edge["proc"].terminate()
    meta = {"startedAt": ctx.started_at, "finishedAt": dt.datetime.now().isoformat(timespec="seconds"), "base": ctx.base,
            "requests": ctx.all_count, "psps": {k: {"id": v["id"], "code": v["code"], "merchants": [m["id"] for m in v["merchants"]]}
                                               for k, v in ctx.psps.items()}, "edge": {k: v for k, v in ctx.edge.items() if k in ("edge_id", "node_id")}}
    ctx.raw_log.close()
    j, m = simlib.write_results(ctx.out_dir, ctx.run_id, meta, scs)
    simlib.write_results(ctx.raw_dir, ctx.run_id, meta, scs)
    print(f"results: {j}\n         {m}")
    bad = [s for s in scs if s.status in ("FAIL", "ERROR")]
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
