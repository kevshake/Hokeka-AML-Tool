# KYC Document Evidence

## Scope

The KYC evidence workflow stores merchant documents, expiry dates, integrity metadata, malware-scan evidence, manual
review decisions, and audit events. Every operation is scoped from the authenticated user's PSP through the owning
merchant. Client-supplied `X-PSP-ID` values never grant access.

## Workflow

1. An authorized user completes full merchant onboarding with legal identity, PSP ownership, CBK source fields, and
   one or more beneficial owners.
2. The backend validates each owner has a date of birth, nationality, positive ownership percentage, and either a
   national ID or passport. The declared ownership total cannot exceed 100 percent.
3. The onboarding service persists the merchant and owners, performs sanctions/PEP and corporate-intelligence checks,
   calculates risk, records audit evidence, and creates a compliance case for review or rejection outcomes.
4. An authorized user uploads a PDF, JPEG, or PNG document for a merchant and may provide an expiry date.
5. The backend validates size, filename, MIME declaration, and magic bytes before writing to disk.
6. Production streams the bytes to the private ClamAV daemon with framed `INSTREAM` and fails closed if scanning is
   unavailable. Where scanning is explicitly disabled outside production, evidence is marked `NOT_SCANNED`; it is
   never represented as clean.
7. The backend stores detected MIME, byte count, SHA-256 digest, scan status, engine, threat signature, and scan time.
8. A permitted reviewer approves or rejects the evidence. Rejections require a reason. Reviewer identity, notes,
   method, and timestamp are persisted and written to the audit trail.
9. Preview and download endpoints re-check tenant ownership and emit inline or attachment content disposition.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/merchants/onboard` | Full onboarding, owner screening, risk decision, audit, and case creation |
| `POST` | `/merchants/{merchantId}/documents` | Upload `file`, `type`, and optional `expiryDate` |
| `GET` | `/merchants/{merchantId}/documents` | List evidence and integrity/review metadata |
| `PUT` | `/documents/{documentId}/verify` | Set `approved`; notes are required on rejection |
| `GET` | `/documents/{documentId}/file` | Tenant-safe inline preview |
| `GET` | `/documents/{documentId}/download` | Tenant-safe attachment download |

## Production Configuration

`docker-compose.prod.yml` runs `clamav/clamav:stable` on the private application network and persists virus definitions.
The backend stores evidence under `/var/lib/hokeka/documents` on a dedicated volume.

```properties
app.document.max-size-bytes=10485760
app.document.antivirus.enabled=true
app.document.antivirus.required=true
app.document.antivirus.host=clamav-prod
app.document.antivirus.port=3310
```

## Verification Boundary

The current decision is a documented manual compliance review backed by integrity and malware evidence. The existing
Sumsub integration performs sanctions, PEP, and adverse-media screening; it is not presented as OCR, liveness, or
document-authenticity verification. Automated identity verification remains a separate provider integration task.

## Due-Diligence Workspace

`/kyc-documents/{merchantId}` combines risk-based CDD, weighted completeness, missing evidence, beneficial ownership,
EDD, document evidence, and a corporate relationship trail.

- Beneficial-owner declarations support CRUD, ownership-total validation, UBO classification at 25 percent, and live
  sanctions/PEP screening. The fallback screening path remains in `aml-microservice`, where Aerospike is owned.
- Passport and national-ID values use versioned AES-256-GCM encryption. Deterministic HMAC lookup columns find repeated
  identifiers without comparing random-IV ciphertext or exposing raw identifiers.
- EDD explicitly tracks source of funds, source of wealth, an optional required site visit, senior approval,
  family/associate checks, and transaction-purpose review. Every change records actor, previous/new state, note, and
  timestamp in `edd_evidence_events`.
- Merchant record trails include beneficial owners, KYC documents, EDD requests, and evidence events. Identifier
  plaintext, keyed hashes, and physical file paths are excluded from generic record views.
- Merchant updates validate PSP ownership before mutation and store the authenticated actor in audit evidence.
  Settlement account values remain write-only; read APIs expose only a configured flag.
