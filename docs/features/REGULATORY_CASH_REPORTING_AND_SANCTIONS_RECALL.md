# Regulatory Cash Reporting And Sanctions Recall

## Scope

This capability closes two decision-critical gaps:

- Kenya cash transaction reporting based on explicit cash classification and
  approved USD-conversion evidence.
- Sanctions candidate retrieval that accounts for aliases, phonetic variants,
  spelling changes, and internal name fragments without moving Aerospike into
  the main backend.

## Kenya Cash Reporting

The transaction ingestion contract accepts `cashTransaction`. Non-cash
transactions do not enter the CTR or LCTR datasets.

For cash transactions, the rules pipeline stores:

- original amount and currency
- USD equivalent
- reporting threshold
- conversion rate and source
- rate effective time
- evaluation status and evaluation time

The configured threshold is USD 15,000 equivalent. Non-USD conversion is valid
only when the currency rate is active, regulatory-approved, sourced, effective,
not expired, and no older than the configured maximum age. Missing or stale
evidence produces a `HOLD`; it never produces a below-threshold result.

Repeated cash activity between 80 percent and 100 percent of the threshold
within 24 hours produces `CASH_STRUCTURING_24H`, an SAR/STR requirement, and a
hold for review.

The operator surface is under Regulatory Reports > Regulatory FX. Approval
mutations are limited to platform administrators; compliance users have
read-only visibility.

## Country Risk

FATF call-for-action jurisdictions produce a compliance hold for policy-based
EDD or countermeasure review. Increased-monitoring status is retained as a risk
signal and does not create an automatic country-wide rejection.

The February 2026 FATF statuses are applied by Flyway migration V196. Country
risk and sanctions-name screening remain separate controls.

## Sanctions Recall

`aml-microservice` generates Aerospike candidate keys from:

- normalized exact names
- all aliases
- token prefixes
- Double Metaphone primary and alternate encodings
- character bigrams

The keys are stored in a LIST bin with a LIST secondary index. Candidate records
are deduplicated and scored using full name similarity. An empty candidate set
or an index-query failure falls back to a full scan for recall. Datastore
unavailability returns `UNAVAILABLE`, which the backend treats as a review
hold.

Aerospike remains exclusively in `aml-microservice`. The main backend uses the
microservice HTTP contract and does not contain an Aerospike repository or
feature store.

## Primary Sources

- Kenya POCAMLA reporting requirements:
  https://new.kenyalaw.org/akn/ke/act/ln/2023/153/eng@2023-11-17
- Kenya Financial Reporting Centre reporting guidance:
  https://www.frc.go.ke/?page_id=25
- FATF February 2026 plenary outcomes:
  https://www.fatf-gafi.org/en/publications/Fatfgeneral/outcomes-FATF-plenary-february-2026.html
- FATF black and grey lists:
  https://www.fatf-gafi.org/en/countries/black-and-grey-lists.html
- Aerospike LIST secondary-index queries:
  https://aerospike.com/docs/develop/data-types/collections/list/index-and-query/
