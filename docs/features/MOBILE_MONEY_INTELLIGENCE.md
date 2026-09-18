# Mobile Money Intelligence

## Scope

The mobile-money workspace is a dedicated e-money control plane. It extends the multi-asset transaction record with observed wallet, device, hashed SIM, agent, merchant, location, float, and network evidence. It does not infer missing PSP telemetry and does not store raw IMSI, ICCID, MSISDN, or phone identifiers.

Aerospike remains isolated in `aml-microservice`; this feature uses the main backend's PostgreSQL transaction, investigation, alert, and reporting stores.

## Ingestion

`POST /api/v1/mobile-money/transactions` accepts:

- customer, wallet, counterparty, amount, currency, event type, and execution time;
- optional USD equivalent for currency-independent high-value and structuring controls;
- optional device and agent-device fingerprints;
- optional PSP-generated `simIdentifierHash` with a minimum length of 16 characters;
- optional agent, merchant, cell, coordinates, SIM-change, device-registration, prior-activity, and agent-float evidence;
- optional cross-border status and provider metadata.

The endpoint is idempotent through the existing `(psp_id, external_transaction_id)` contract. Event type and transaction type must agree. Coordinates must be complete pairs and fall within geographic bounds. Metadata containing raw SIM or phone identifier keys is rejected.

## Detection Scenarios

- Shared device across multiple wallets.
- Shared hashed SIM identifier across multiple wallets.
- Structuring across device-linked wallets when USD-equivalent data is present.
- Rapid cash-in followed by materially similar cash-out.
- Rapid circulation across several counterparties.
- Repeated wallet-pair cluster activity.
- Recent SIM replacement before a high-value transaction.
- New device before a high-value transaction.
- Customer and agent sharing a device or crossing device roles.
- Agent-wallet concentration and inconsistent agent-float movement.
- Configured night-window activity.
- Geographically impossible velocity based on two observed coordinate/time pairs.
- Merchant refund abuse and merchant pass-through movement.
- Cross-border receipt followed by domestic fragmentation.
- Dormant wallet reactivation based on provider-supplied prior activity.

Every scenario creates an explainable `MultiAssetRiskSignal` with `AML` or `FRAUD` taxonomy, immutable evidence, score impact, severity, and source transaction. Combined risk updates the transaction decision and customer risk tier. Material mobile scores create a PSP-scoped `MOBILE_MONEY_NETWORK` alert.

## Network And Risk

Observed activity updates durable edges for customer-to-wallet ownership, wallet-to-device, wallet-to-hashed-SIM, wallet-to-agent, agent-to-device, wallet-to-merchant, wallet-to-counterparty, and wallet-to-device/SIM-cluster relationships.

Risk profiles are maintained separately for customer, wallet, device, SIM, agent, merchant, and network cluster. Profile scores preserve the highest observed risk; signal counts only include scenarios relevant to that entity type.

## Investigation APIs

- `GET /api/v1/mobile-money/transactions`
- `GET /api/v1/mobile-money/risk-profiles`
- `GET /api/v1/mobile-money/network?entityType=WALLET&entityReference=...`

The frontend workspace at `/mobile-money` supports activity review, transaction ingestion, entity-risk ranking, bidirectional network traversal, and links to generic record detail for contexts, profiles, edges, transactions, and customers.

Customer and alert record trails include mobile-money source contexts and occurrences. Report output aliases are mapped to the same record-detail routes.

## Reports

- `MM_001` Mobile Money Risk Signal Report.
- `MM_002` Mobile Money Entity Risk Report.
- `MM_003` Mobile Money Network Activity Report.

The reports include source identifiers, risk calculations, relationship counts, signal aggregates, and evidence fields. Reruns read current persisted source data using the report execution parameter set and preserve report-to-record links.

## Configuration

All thresholds can be set through environment variables:

- `MOBILE_MONEY_HIGH_VALUE_USD`
- `MOBILE_MONEY_SHARED_DEVICE_WALLETS`
- `MOBILE_MONEY_SHARED_SIM_WALLETS`
- `MOBILE_MONEY_RAPID_FLOW_MINUTES`
- `MOBILE_MONEY_DORMANT_DAYS`
- `MOBILE_MONEY_IMPOSSIBLE_SPEED_KPH`
- `MOBILE_MONEY_NIGHT_START_HOUR`
- `MOBILE_MONEY_NIGHT_END_HOUR`

Schema migrations are `V167__mobile_money_network_intelligence.sql` and `V168__mobile_money_reports.sql`.
