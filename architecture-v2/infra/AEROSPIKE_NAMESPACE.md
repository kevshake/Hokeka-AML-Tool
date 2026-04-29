# Aerospike Namespace & Set Layout — AML

The default `aerospike/aerospike-server:6.4` image ships with namespace `test`. For
production, replace `/etc/aerospike/aerospike.conf` with the layout below to provision
the dedicated `aml` namespace with persistence and a 4 GiB memory budget.

## Namespace `aml`

```conf
namespace aml {
    memory-size 4G
    default-ttl 0  # explicit per-set TTLs override

    storage-engine device {
        file /opt/aerospike/data/aml.dat
        filesize 16G
    }

    nsup-period 120
}
```

Mount this file into the `aml-aerospike` container at `/etc/aerospike/aerospike.conf`.

## Sets

| Set | Key pattern | TTL (default) | Bins |
|---|---|---|---|
| `txn_hot` | `{psp_id}:{txn_id}` | 1 h | txn_id, psp_id, pan_hash, merchant_id, amount_c, currency, risk, decision, txn_ts |
| `pan_velocity` | `{psp_id}:{pan_hash}` | 24 h | count_1m, count_1h, count_24h, total_24h |
| `merchant_velocity` | `{psp_id}:{merchant_id}` | 24 h | count_1h, count_24h, flagged_count |
| `blacklist` | `{psp_id}:{pan_hash}` | rule-defined | blocked_until, reason_code |

TTLs are passed per-write via `WritePolicy.expiration` from `TxnHotCache`, so the
namespace `default-ttl` of 0 (never expire by default) is intentional.

## Quick verification

```bash
docker exec -it aml-aerospike asadm -e "info namespace"
docker exec -it aml-aerospike asadm -e "show sets"
```
