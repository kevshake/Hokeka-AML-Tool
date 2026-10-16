"""Detect (and, with --apply, patch in the SIM database only) schema defects that block a fresh
Flyway-built Control Plane database. Every defect found is written to
scripts/sim/results/db-workarounds.json so the simulator reports it as a product failure — the
patch exists only so the rest of the simulation can run. Never run this against a real database.

Checks:
  1. Hibernate Envers needs sequence `revinfo_seq` (DefaultRevisionEntity, pooled, increment 50);
     Flyway only creates `revinfo` with an identity column.
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


def main():
    apply = "--apply" in sys.argv
    found = []
    if not psql("select 1 from information_schema.sequences where sequence_schema='public' and sequence_name='revinfo_seq'"):
        found.append({"defect": "missing sequence revinfo_seq (Envers DefaultRevisionEntity)",
                      "fix": "CREATE SEQUENCE IF NOT EXISTS revinfo_seq START WITH 1 INCREMENT BY 50;"})
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
