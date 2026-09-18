# Virtual Asset Compliance

> **Status:** Implemented | **Last updated:** July 2026

The virtual-asset workspace connects VASP due diligence, recurring wallet exposure screening, Travel Rule handling, read-only regulator access, record trails, alerts, and reports. The application route is `/wallet-intelligence`; all operator data is loaded from backend APIs and no fixture or fallback data is used.

## Controls

- VASP directory: licence and regulator details, sanctions state, beneficial ownership, attributed wallet clusters, supported Travel Rule protocols, risk score, transfer decision, and review dates.
- Wallet profiles: links a real Customer 360 crypto account and public address to a VASP, network, ownership type, and screening schedule.
- Wallet screening: onboarding, address-added, deposit, pre-withdrawal, periodic, and manual triggers with direct and indirect exposure, attribution depth, categories, and provider references. Transaction ingestion persists the exact provider response used by risk decisioning, including external counterparty addresses that are not registered wallet profiles.
- Travel Rule: transaction-time jurisdiction policy selection, required-field checks, originator and beneficiary verification evidence, VASP transfer controls, protocol validation, encrypted payload storage, and delivery attempts.
- Regulator access: time-limited, hashed one-time keys with explicit scopes, optional source-IP allowlists, and immutable access logs.

Disabled, timed-out, malformed, or failed blockchain-provider responses produce an unavailable result. They never produce a clean screening decision. A pre-withdrawal outage creates an alert, and Travel Rule processing fails closed when a jurisdiction policy or USD-equivalent value is unavailable.

## Data And Retention

Flyway migrations `V171` through `V179` create and extend the VASP directory, wallet profiles, append-only wallet and VASP screening evidence, jurisdiction policies, Travel Rule transfers and attempts, regulator grants and logs, report definitions, identity-verification provenance, transaction screening links, and report-query indexes.

Travel Rule identity payloads are serialized and encrypted by `AesGcmStringConverter` with AES-256-GCM. `SECURITY_ENCRYPTION_KEY` must decode to exactly 32 bytes; there is no plaintext fallback. New multi-asset transaction writes do not copy originator or beneficiary names and accounts into legacy plaintext columns.

Screening records, transmission attempts, and regulator access logs are append-only at the database layer. Evidence and transfer records retain an explicit `retain_until` date of at least seven years. Each identity decision stores the evidence reference, authenticated verifier, and decision timestamp.

The Kenya baseline policy applies to all transfers from its effective date, requires both-party verification and core originator/beneficiary fields, and is seeded for existing and newly created PSPs. Compliance administrators can version operational requirements by ending one policy and enabling another; the policy active at the transaction time is selected.

## API

Authenticated operator endpoints are below `/api/v1/virtual-assets` and derive PSP scope from the authenticated user.

| Method | Path | Purpose |
|---|---|---|
| `GET/POST/PUT` | `/vasps`, `/vasps/{id}` | Search, create, and update VASP due diligence |
| `POST` | `/vasps/{id}/screen` | Screen legal/trading names and beneficial owners through the AML microservice |
| `GET/POST` | `/wallets` | List and register crypto wallets; registration triggers screening |
| `POST` | `/wallets/{id}/screen` | Run a real provider screen for a declared trigger |
| `GET` | `/wallet-screenings` | Page immutable screening evidence |
| `GET` | `/transactions` | Select real crypto transactions for Travel Rule cases |
| `GET/POST/PUT` | `/travel-rule/policies`, `/travel-rule/policies/{id}` | Govern jurisdiction policies |
| `GET/POST` | `/travel-rule/transfers` | List and prepare encrypted transfers |
| `PUT` | `/travel-rule/transfers/{id}/verify` | Record evidence-backed identity decisions |
| `POST` | `/travel-rule/transfers/{id}/transmit` | Transmit a ready transfer through the configured gateway |
| `GET/POST/DELETE` | `/regulator-grants`, `/regulator-grants/{id}` | Issue, list, and revoke regulator grants |

External read-only feeds are `GET /api/v1/regulator/virtual-assets/transactions`, `/wallet-screenings`, and `/travel-rule-transfers`. Every feed requires `X-Regulator-Access-Key`, `from`, and `to`; each validates its matching scope. Travel Rule PII is returned only when `includePii=true` and the grant also contains `TRAVEL_RULE_PII`.

Supported grant scopes are `TRANSACTIONS_READ`, `WALLET_SCREENING_READ`, `TRAVEL_RULE_READ`, and `TRAVEL_RULE_PII`. A scope cannot be used against a different feed, and PII access always requires both `TRAVEL_RULE_READ` and `TRAVEL_RULE_PII`.

## Provider Configuration

```properties
blockchain.analytics.enabled=true
blockchain.analytics.provider=YOUR_PROVIDER
blockchain.analytics.base-url=https://blockchain-gateway.example
blockchain.analytics.screening-path=/v1/wallets/screen
blockchain.analytics.api-key=${BLOCKCHAIN_ANALYTICS_API_KEY}
blockchain.analytics.timeout=3s
crypto.wallet-screening.cron=0 */15 * * * *
crypto.vasp-screening.cron=0 0 */6 * * *
crypto.vasp-screening.retry-cron=0 */30 * * * *

travel-rule.gateway.enabled=true
travel-rule.gateway.base-url=https://travel-rule-gateway.example
travel-rule.gateway.transmission-path=/v1/transfers
travel-rule.gateway.api-key=${TRAVEL_RULE_GATEWAY_API_KEY}
travel-rule.gateway.timeout=5s
```

Both real gateways require HTTPS except for loopback development. Travel Rule requests carry a stable transaction reference and an idempotency key. Every attempt is retained even when the provider is unavailable or rejects the request.

VASP sanctions screening calls the independently deployed AML microservice. Clean, possible-match, and unavailable outcomes are persisted distinctly; an outage never becomes a clean decision. Scheduled screening uses the stored next-screening and retry state, and each run produces immutable evidence consumed by `VA_005`.

## Record Trails And Reports

Every workspace row links to `/records/:recordType/:recordId`. Wallet detail links its customer, account, VASP, screening history, transactions, and Travel Rule case. VASP detail links attributed wallets; transactions link wallet screens and Travel Rule evidence; regulator grants link immutable access logs.

Report definitions consume the same persisted sources and can be rerun with current parameters:

- `VA_001`: wallet screening and exposure history.
- `VA_002`: Travel Rule audit, policy, identity evidence, status, and delivery attempts.
- `VA_003`: VASP due-diligence directory and review state.
- `VA_004`: regulator grant and access audit.
- `VA_005`: append-only VASP sanctions and beneficial-owner screening history.

Report runs persist source IDs, parameters, row counts, totals, calculations, and run timestamps through the shared report-provenance service.

## Service Boundary

Aerospike remains in the independently deployed `aml-microservice` for sanctions and low-latency AML lookups. The main backend does not add another Aerospike repository. Virtual-asset compliance evidence, relationships, and reporting provenance remain in PostgreSQL, with external screening and Travel Rule integrations reached through HTTPS clients.

## Regulatory References

- [Kenya Virtual Asset Service Providers Act, 2025](https://new.kenyalaw.org/akn/ke/act/2025/20)
- [FATF targeted update on virtual assets and VASPs](https://www.fatf-gafi.org/en/publications/Fatfrecommendations/Targeted-update-virtual-assets-vasps.html)
