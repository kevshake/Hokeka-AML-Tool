# AML Fraud Detector - API Documentation

## Overview

The AML Fraud Detector exposes a comprehensive REST API documented using **SpringDoc OpenAPI 2** (Swagger UI).

- **Swagger UI**: `http://localhost:2637/api/v1/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:2637/api/v1/v3/api-docs`

## Authentication

Most endpoints require JWT Bearer token authentication.

1. Login via `POST /api/v1/auth/login`
2. Use the returned `token` in the `Authorization: Bearer <token>` header.

## Key API Groups

| Tag                  | Description                              | Base Path                  |
|----------------------|------------------------------------------|----------------------------|
| Auth                 | Authentication & Session Management      | `/auth/**`                 |
| Users                | User & Role Management                   | `/users/**`                |
| Alerts               | Fraud & AML Alerts                       | `/alerts/**`               |
| Cases                | Case Management & Investigation          | `/cases/**`                |
| Transactions         | Transaction Ingestion & Monitoring       | `/transactions/**`         |
| Screening            | Sanctions & PEP Screening                | `/screening/**`            |
| Reports              | Regulatory & Operational Reports         | `/reports/**`              |
| PSP                  | Payment Service Provider Management      | `/psps/**`                 |
| Billing              | SaaS Billing & Invoicing                 | `/billing/**`              |
| Compliance           | SAR, KYC, Regulatory Reporting           | `/compliance/**`           |
| Analytics            | Dashboards, Risk Analytics, Metrics      | `/analytics/**`            |

## Running the Application

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

As of May 2026, the OpenAPI configuration is present but most controllers still lack detailed operation-level documentation. Full annotation coverage is in progress.

## Related Files

- `BACKEND/src/main/java/com/posgateway/aml/config/OpenApiConfig.java`
- Controllers under `controller/` package

---

**Last Updated:** May 2026
**Owner:** Lappie Ya Home (Senior Developer)