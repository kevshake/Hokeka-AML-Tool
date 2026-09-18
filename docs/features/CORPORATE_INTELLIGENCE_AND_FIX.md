# Corporate Intelligence and FIX Market Ingestion

## Scope

This implementation closes two production data gaps:

- merchant legal-entity verification and adverse-media evidence during onboarding
  and periodic KYC refresh;
- authenticated FIX 4.4 order, cancellation, rejection, and execution ingestion into
  the existing market-surveillance scenarios.

Neither path fabricates a successful result. Provider unavailability creates durable
`UNAVAILABLE` evidence and an alert. FIX remains disabled until a real session file,
credentials, PSP mapping, and persistent message store are configured.

## Corporate intelligence

### Providers

- OpenCorporates API v0.4 performs an exact jurisdiction/company-number lookup first,
  then a scored company search if the exact lookup is not available.
- GDELT DOC 2.0 searches a rolling one-year window for the legal/trading name plus
  configured financial-crime terms. Returned articles are investigator leads, not
  an automated allegation or final disposition.

Official references:

- https://api.opencorporates.com/documentation/API-Reference
- https://blog.gdeltproject.org/gdelt-doc-2-0-api-debuts/
- https://blog.gdeltproject.org/doc-geo-2-0-api-updates-full-year-searching-and-more/

### Decision behavior

`CorporateIntelligenceService` persists every check in
`corporate_intelligence_checks` with:

- provider availability and query provenance;
- registry candidates and the selected legal-entity match;
- adverse-media article title, URL, source, language, country, and seen date;
- deterministic registry and risk scores;
- decision reason, actor, timestamp, seven-year retention date, and SHA-256 evidence hash.

Only an active registry match plus an available adverse-media result with no leads is
`CLEAR`. No match, a potential match, an inactive company, or media leads is `REVIEW`.
Any unavailable dependency is `UNAVAILABLE`. Both non-clear states create a unified
merchant alert and prevent low-risk onboarding auto-approval.

Checks run:

- inline during full merchant onboarding;
- when a quick merchant draft is created;
- manually from the KYC Intelligence tab;
- periodically according to `corporate.intelligence.refresh-cron`, with one database
  transaction per merchant.

### Configuration

```properties
CORPORATE_REGISTRY_ENABLED=true
OPENCORPORATES_API_TOKEN=<licensed token>
ADVERSE_MEDIA_ENABLED=true
CORPORATE_INTELLIGENCE_REFRESH_CRON=0 30 2 * * *
```

The startup environment validator requires the OpenCorporates token whenever registry
verification is enabled.

## FIX 4.4 ingestion

The backend embeds QuickFIX/J 3.0 with persistent file stores and supports one or more
sessions in either acceptor or initiator mode. A single settings file cannot mix modes.

Official references:

- https://quickfixj.org/docs/overview/
- https://quickfixj.org/docs/developer-docs/
- https://quickfixj.org/docs/architecture/

### Supported application messages

| MsgType | FIX message | Platform action |
|---|---|---|
| `D` | NewOrderSingle | Creates or idempotently resolves a market order, then evaluates layering |
| `F` | OrderCancelRequest | Cancels an order and evaluates rapid large-order cancellation |
| `8` | ExecutionReport fill/trade | Creates an execution and evaluates wash trade, off-market price, marking the close, and repeated counterparty behavior |
| `8` | ExecutionReport cancelled/rejected | Applies the terminal order state with retained reason/evidence |

The same `MarketSurveillanceService` methods serve REST and FIX. FIX callbacks pass the
PSP ID from validated session configuration; they do not depend on an HTTP security
context and cannot access another PSP's customers or orders.

### Authentication and recovery

- Acceptor logons require FIX `Username(553)` and `Password(554)` matching the
  credentialed session file.
- Initiator logons inject those fields.
- QuickFIX/J sequence numbers and messages use `FileStoreFactory`; reset-on-logon,
  reset-on-logout, and reset-on-disconnect are disabled in the supplied configuration.
- Every inbound application sequence is idempotent by session, sequence, and direction.

### Evidence and privacy

`fix_message_events` stores:

- session, message type, sequence, sending and receive times;
- a safe allowlist of business fields and configured custom mapping tags;
- SHA-256 of the complete received message;
- accepted/rejected/ignored outcome and bounded error detail;
- linked market order and execution IDs.

Raw FIX application messages and logon credentials are not stored in PostgreSQL.
The Market Surveillance FIX Feed tab shows live session state, sequence numbers,
message outcomes, evidence hashes, and links to every related record.

### Deployment

Start from `infra/fix/hokeka-fix44.cfg.example`, create the ignored
`infra/fix/hokeka-fix44.cfg`, and set:

```properties
FIX_ENABLED=true
FIX_SETTINGS_PATH=/etc/hokeka/fix/hokeka-fix44.cfg
FIX_ACCEPT_PORT=9878
```

Production Compose mounts the settings read-only and stores QuickFIX/J sequence/message
state in the durable `fix_prod_data` volume. Startup fails if the enabled settings omit
credentials, PSP ownership, store/log paths, or network settings.

## Reports and record trails

- `KYC_004` reports complete corporate-intelligence evidence.
- `MKT_003` reports FIX message receipts and links to orders/executions.
- Merchant record trails include every intelligence check and resulting alert.
- Corporate checks, FIX receipts, market orders, executions, signals, and alerts are
  mutually navigable through record detail and report preview links.

Aerospike remains exclusively inside `aml-microservice`; neither integration adds an
Aerospike client or configuration to the main backend.
