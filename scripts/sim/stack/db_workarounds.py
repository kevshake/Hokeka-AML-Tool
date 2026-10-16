"""Detect (and, with --apply, patch in the SIM database only) schema defects that block a fresh
Flyway-built Control Plane database. Every defect found is written to
scripts/sim/results/db-workarounds.json so the simulator reports it as a product failure — the
patch exists only so the rest of the simulation can run. Never run this against a real database.

Checks:
  1. The Envers revision table `revinfo` has a generated `rev` key (the revision entity maps it).
  2. Every *_aud table must carry the columns of its base table (Envers writes all audited columns).

Usage: py scripts/sim/stack/db_workarounds.py [--apply]
"""
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "results", "db-workarounds.json")


def psql(sql):
    p = subprocess.run(["docker", "exec", "-i", "hokeka-sim-postgres-1", "psql", "-U", "hokeka_sim", "-d",
                        "fraud_detector_sim", "-At", "-v", "ON_ERROR_STOP=1", "-c", sql], capture_output=True, text=True)
    if p.returncode != 0:
        raise SystemExit("psql failed: " + p.stderr)
    return [l for l in p.stdout.splitlines() if l.strip()]


def detect():
    """Read-only: returns the schema defects found in the live sim database (no writes)."""
    found = []
    # Since V254 the Envers revision entity maps revinfo's identity column (no revinfo_seq), so the
    # check is that revinfo exists with an identity/default-generated primary key.
    if not psql("select 1 from information_schema.columns where table_schema='public' and table_name='revinfo' "
                "and column_name='rev' and (is_identity='YES' or column_default is not null)"):
        found.append({"defect": "revinfo.rev is not identity/default-generated (Envers revision entity)",
                      "fix": "-- needs a Flyway migration; no sim patch"})
    drift = psql("""
select a.table_name||'|'||c.column_name||'|'||case when c.data_type='USER-DEFINED' then c.udt_name else c.data_type end
from information_schema.tables a
join information_schema.columns c on c.table_name = regexp_replace(a.table_name,'_aud$','') and c.table_schema='public'
where a.table_schema='public' and a.table_name like '%\\_aud'
and not exists (select 1 from information_schema.columns x where x.table_schema='public' and x.table_name=a.table_name and x.column_name=c.column_name)
order by 1""")
    for row in drift:
        t, col, typ = row.split("|")
        found.append({"defect": f"{t} lacks column {col} present on {t[:-4]}",
                      "fix": f"ALTER TABLE {t} ADD COLUMN IF NOT EXISTS {col} {typ};"})
    found += unmapped_not_null()
    return found


ENTITY_DIR = os.path.join(HERE, "..", "..", "..", "BACKEND", "src", "main", "java", "com", "posgateway", "aml", "entity")


def _snake(name):
    import re
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def entity_columns():
    """table -> column names mapped by @Entity classes (explicit name="..." plus snake_case fields)."""
    import re
    tables = {}
    for d, _, files in os.walk(ENTITY_DIR):
        for f in files:
            if not f.endswith(".java"):
                continue
            src = open(os.path.join(d, f), encoding="utf-8", errors="replace").read()
            m = re.search(r'@Table\(\s*name\s*=\s*"([^"]+)"', src)
            if not m or "@Entity" not in src:
                continue
            cols = set(re.findall(r'name\s*=\s*"([a-z0-9_]+)"', src))
            cols.update(_snake(x) for x in re.findall(r"private\s+[\w<>, .]+\s+(\w+)\s*[;=]", src))
            tables.setdefault(m.group(1), set()).update(cols)
    return tables


def unmapped_not_null():
    """NOT NULL columns without a default that the owning entity never writes: every insert fails."""
    tables = entity_columns()
    rows = psql("""
select c.table_name||'|'||c.column_name from information_schema.columns c
join information_schema.tables t on t.table_name=c.table_name and t.table_schema=c.table_schema
where c.table_schema='public' and t.table_type='BASE TABLE' and c.is_nullable='NO'
  and c.column_default is null and c.is_identity='NO'""")
    out = []
    for row in rows:
        t, c = row.split("|")
        if t in tables and c not in tables[t] and c != "id" and not has_fill_trigger(t):
            out.append({"defect": f"{t}.{c} is NOT NULL without default but not mapped by its entity (inserts fail)",
                        "fix": "-- needs a Flyway migration; no sim patch"})
    return out


def has_fill_trigger(table):
    """Tables whose unmapped NOT NULL column is filled by a BEFORE INSERT trigger (e.g. V255)."""
    return bool(psql(f"select 1 from pg_trigger g join pg_class k on k.oid=g.tgrelid "
                     f"where k.relname='{table}' and not g.tgisinternal"))


def main():
    apply = "--apply" in sys.argv
    found = detect()
    applied = []
    if apply and found:
        for f in found:
            psql(f["fix"])
            applied.append(f["fix"])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    prev = []
    if os.path.exists(OUT):
        try:
            prev = json.load(open(OUT, encoding="utf-8")).get("defects", [])
        except Exception:
            prev = []
    merged = prev + [f for f in found if f not in prev]
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump({"defects": merged, "appliedThisRun": applied}, fh, indent=2)
    print(f"{len(found)} defect(s) found; {len(applied)} sim-only patch(es) applied; report: {os.path.abspath(OUT)}")
    for f in found:
        print(" -", f["defect"])


if __name__ == "__main__":
    main()
