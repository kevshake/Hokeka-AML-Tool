# AML Fraud Detector - API Documentation

## Overview

The AML Fraud Detector exposes a comprehensive REST API documented using **SpringDoc OpenAPI 2** (Swagger UI).

- **Swagger UI**: `http://localhost:2637/api/v1/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:2637/api/v1/v3/api-docs`

## Authentication

Interactive application endpoints use the authenticated Spring Security session cookie.

1. Login via `POST /api/v1/auth/login`.
2. Retain the returned session cookie and send it with subsequent requests.

## Key API Groups

| Tag                  | Description                              | Base Path                  |
|----------------------|------------------------------------------|----------------------------|
| Auth                 | Authentication & Session Management      | `/auth/**`                 |
| Users                | User & Role Management                   | `/users/**`                |
| Alerts               | Fraud & AML Alerts                       | `/alerts/**`               |
| Cases                | Case Management & Investigation          | `/cases/**`                |
| Transactions         | Transaction Ingestion & Monitoring       | `/transactions/**`         |
| Merchants            | Full onboarding, KYC ownership and risk  | `/merchants/**`            |
| Screening            | Sanctions & PEP Screening                | `/screening/**`            |
| Reports              | Regulatory & Operational Reports         | `/reports/**`              |
| PSP                  | Payment Service Provider Management      | `/psps/**`                 |
| Billing              | SaaS Billing & Invoicing                 | `/billing/**`              |
| Compliance           | SAR, KYC, Regulatory Reporting           | `/compliance/**`           |
| Multi-Asset AML      | Customer 360, linked accounts, normalized transactions, risk signals | `/multi-asset/**` |
| Record Traceability  | PSP-scoped detail graphs, related records, occurrences and report provenance | `/records/{recordType}/{recordId}` |
| Analytics            | Dashboards, Risk Analytics, Metrics      | `/analytics/**`            |
| Risk Management      | Country Risk, High-Risk Countries        | `/risk/**`                 |
| Monitoring           | Transaction Monitoring, Reports          | `/monitoring/**`           |
| Fraud Intelligence   | Cross-PSP Fraud Sharing                  | `/fraud-intelligence/**`   |
| Subscriptions        | PSP Subscription & Tier Management       | `/subscriptions/**`        |
| Payments             | M-Pesa & Bank Transfer Payments          | `/billing/payments/**`     |
| CBK Compliance       | Central Bank of Kenya GDI Reporting      | `/compliance/cbk/**`       |

## Running the Application

### Multi-Asset Customer 360

The multi-asset API is PSP scoped from the authenticated principal. It supports customer creation and search, linked asset accounts, idempotent transaction ingestion, Customer 360 aggregation, and a risk-signal feed. See [Multi-Asset AML and Customer 360](features/MULTI_ASSET_AML.md) for request fields, scenarios, provider behavior, and configuration.

Non-`ALLOW` assessments are written into the standard alert queue and can be escalated through the standard case workflow.

```bash
# Development
cd BACKEND
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Then open:
# http://localhost:2637/api/v1/swagger-ui.html
```

## Production Considerations

- In production, Swagger UI can be disabled via:
  ```properties
  springdoc.api-docs.enabled=false
  springdoc.swagger-ui.enabled=false
  ```
- Or protected behind authentication.

## Best Practices

- All new endpoints **must** include:
  - `@Tag(name = "...")` at class level
  - `@Operation(summary = "...", description = "...")`
  - `@ApiResponse` annotations for success and error cases
- Use meaningful response DTOs instead of raw entities.
- Document all request/response examples using `@ExampleObject`.

## Current Status

Full API groups documented. For comprehensive PSP integration guide
with authentication, billing, webhooks, and code examples in Python,
JavaScript, Java, and cURL, see:

➡️ **[PSP API Guide](PSP_API_GUIDE.md)**

## Related Files

- `BACKEND/src/main/java/com/posgateway/aml/config/OpenApiConfig.java`
- Controllers under `controller/` package

---

**Last Updated:** July 2026
**Owner:** Lappie Ya Home (Senior Developer)
