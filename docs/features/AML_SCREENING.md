# AML Screening & Sanctions Compliance

## Overview

Real-time screening of individuals, entities, and transactions against sanctions lists (OFAC, UN, EU) and custom watchlists. Uses fuzzy name matching via DoubleMetaphone + Levenshtein distance for high match accuracy.

## Screening Architecture

```
Input Name / Entity
    │
    ▼
NameMatchingService
    │ DoubleMetaphone phonetic encoding
    │ Levenshtein distance scoring
    │ Phonetic + distance≤3 OR similarity≥80% → MATCH
    ▼
SanctionsScreeningService
    │ Aerospike L1 cache (prefix-indexed for O(log n))
    │ Full list scan fallback
    │ Matches against OFAC, UN, EU, UK sanctions lists
    ▼
CustomWatchlistService
    │ PSP-specific watchlists (DB-backed, Aerospike cached)
    │ Manual entries, batch imports
    ▼
ScreeningWhitelistService
    │ False-positive whitelist (override matches)
    │ PSP-level and system-level whitelists
    ▼
Result: { matchFound, similarity, matches[], screeningResult }
```

## Name Matching Algorithm

1. **Phonetic encoding**: Both input and reference names are encoded using DoubleMetaphone
2. **Exact match**: If raw strings match exactly → score = 1.0
3. **Phonetic match**: If metaphone codes match AND Levenshtein distance ≤ 3 → MATCH
4. **Fuzzy match**: If Levenshtein similarity ≥ 0.80 → MATCH
5. **Thresholds**: Configurable via `sanctions.matching.*` properties

## Watchlist Types

| Watchlist | Source | Update Frequency |
|---|---|---|
| OFAC SDN | US Treasury | Daily |
| UN Consolidated | United Nations | Monthly |
| EU Consolidated | European Union | Monthly |
| UK Sanctions | HM Treasury | Monthly |
| Custom PSP | PSP-managed | Real-time |
| Cross-PSP Fraud | Auto-populated | Real-time |

## Cross-PSP Fraud Intelligence

When a TRUE_POSITIVE alert is resolved, the system automatically:
1. Creates a CrossPspFraudFlag record (entity_value, entity_type, source_psp_id)
2. Flags: MERCHANT_ID, PAN_HASH, TERMINAL, NAME (trading name)
3. Increments flag_count on repeat hits
4. Escalates risk MEDIUM → HIGH on multi-PSP repeats

When a new transaction arrives, the DecisionEngine checks:
- Exact match: merchant ID, PAN hash, terminal flagged by ANY PSP → BLOCK
- Fuzzy match: merchant trading name vs flagged NAME entries → HOLD/BLOCK

## Screening Page

**ScreeningPage**: Simple screening tool — enter a name, get instant results against all watchlists:
- Name input with search button
- Results display with match/no-match badge
- Match list with details (list name, score, category)
- Full result detail view

## Performance

- Aerospike prefix index (idx_sanctions_prefix) on 2-char name prefix
- Reduces scan from 500K → ~740 candidates per lookup
- Screen complete in <5ms for 95% of queries