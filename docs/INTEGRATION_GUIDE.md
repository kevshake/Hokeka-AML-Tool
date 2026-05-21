# Hokeka AML Fraud Detector - Integration Guide

> **Modern AML & Fraud Detection for Financial Institutions and PSPs**

---

## Introduction

The Hokeka AML Fraud Detector is a powerful, real-time Anti-Money Laundering and Fraud Detection platform designed for Payment Service Providers, Banks, and Fintech companies.

This guide will help you integrate the AML system into your existing infrastructure quickly and effectively.

### What You Can Do With This API

- Monitor transactions in real-time for fraud and AML risks
- Screen users, counterparties, and payment details against sanctions lists
- Automatically generate regulatory reports (SAR, CTR, etc.)
- Manage compliance cases and investigations
- Configure custom rules and risk thresholds
- Receive alerts and take action on suspicious activity

---

## Getting Started

### 1. Authentication

All requests (except login) require a JWT Bearer token.

**Login Endpoint**

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "your_username",
  "password": "your_password"
}
```

**Response**

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 86400
}
```

Include the token in subsequent requests:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 2. Base URLs

| Environment | Base URL                          |
|-------------|-----------------------------------|
| Local       | `http://localhost:2637/api/v1`    |
| Staging     | `https://testapi.hokeka.com/api/v1` |
| Production  | `https://api.hokeka.com/api/v1`   |

---

## Core Integration Flows

### Transaction Monitoring (Recommended)

Send transactions in real-time for instant risk scoring.

**Endpoint:** `POST /api/v1/transactions`

**Example Request**

```json
{
  "transactionId": "txn_123456789",
  "userId": "user_98765",
  "amount": 125000.00,
  "currency": "KES",
  "direction": "OUTBOUND",
  "counterpartyId": "merchant_456",
  "paymentMethod": "BANK_TRANSFER",
  "ipAddress": "41.204.187.12",
  "deviceFingerprint": "device_xyz",
  "timestamp": "2026-05-20T14:30:00Z"
}
```

**Response**

```json
{
  "transactionId": "txn_123456789",
  "riskScore": 87,
  "decision": "FLAG",
  "triggeredRules": ["R-2", "R-7", "R-30"],
  "alertId": "alert_445566"
}
```

### User Screening

Screen users during onboarding or periodically.

**Endpoint:** `POST /api/v1/screening/user`

---

## Webhooks (Recommended for Real-time Updates)

Instead of polling, subscribe to webhooks to receive real-time notifications.

**Available Webhook Events:**

- `transaction.flagged`
- `alert.created`
- `case.updated`
- `user.screened`
- `report.generated`

**Example Webhook Payload**

```json
{
  "event": "transaction.flagged",
  "timestamp": "2026-05-20T14:32:15Z",
  "data": {
    "transactionId": "txn_123456789",
    "alertId": "alert_445566",
    "riskScore": 87,
    "triggeredRules": ["R-2", "R-7"]
  }
}
```

---

## Error Handling

The API uses standard HTTP status codes.

| Code | Meaning                          | Action                              |
|------|----------------------------------|-------------------------------------|
| 200  | Success                          | -                                   |
| 400  | Bad Request                      | Check request payload               |
| 401  | Unauthorized                     | Refresh token or re-login           |
| 403  | Forbidden                        | Check permissions / PSP scope       |
| 429  | Rate Limited                     | Implement exponential backoff       |
| 500  | Internal Server Error            | Retry with backoff + contact support|

---

## Best Practices

1. **Always send device fingerprint and IP address** — greatly improves detection accuracy.
2. **Use webhooks** instead of polling for better performance.
3. **Implement idempotency** using `transactionId` or `idempotencyKey`.
4. **Start with recommended rules** (marked as "Recommended" in the rule catalog).
5. **Test thoroughly** in the staging environment before going live.

---

## Support & Resources

- **Swagger UI**: `https://api.hokeka.com/api/v1/swagger-ui.html`
- **API Reference**: Available in Swagger
- **Support Email**: support@hokeka.com
- **Integration Help**: Schedule a call with our solutions team

---

**Last Updated:** May 2026

*This documentation is designed to make integration as smooth as possible for technical teams.*