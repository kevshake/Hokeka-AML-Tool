# Notifications

## Overview

The platform supports multiple notification channels: in-app messages, email (via SMTP), and webhooks. Notifications cover alerts, billing events, compliance deadlines, and system status changes.

## Notification Types

| Type | Channel | Trigger |
|---|---|---|
| Alert Created | In-app | New fraud/AML alert |
| Case Assignment | In-app | Case assigned to user |
| Case Decision | In-app + Email | Decision made on case |
| Invoice Generated | Email | Monthly billing cycle |
| Invoice Overdue | Email | 7/14/21/30+ days past due |
| Compliance Deadline | In-app | Upcoming/overdue deadline |
| System Notification | In-app | System-wide announcements |

## Email Notifications

### BillingEmailService

Full HTML email generation with MIME multipart support:

| Method | Purpose | Attachment |
|---|---|---|
| `sendInvoiceEmail()` | New invoice notification | PDF invoice |
| `sendDunningReminderEmail()` | Overdue payment reminder | None |
| `sendEscalationEmail()` | 30+ day overdue escalation | None |

Email templates include:
- Branded header (company name, burgundy theme)
- Invoice summary table (number, period, due date, line items)
- Total amount with currency formatting
- Support contact information
- Dunning urgency escalation (color-coded: burgundy→red)

### Configuration

```yaml
notifications:
  from-address: no-reply@hokeka.com
  email-enabled: true
spring:
  mail:
    host: smtp.example.com
    port: 587
    username: user
    password: pass
```

Email sending is `@Async` and fail-soft (logs warning, never throws).
JavaMailSender is optional — when absent the service gracefully degrades.

## In-App Messages

**MessagesPage**: System notification inbox:
- Unread indicators (blue dot)
- Subject, body, timestamp
- Read/unread toggle on click
- Empty state illustration

## Compliance Deadline Notifications

**ComplianceCalendarPage**:
- Overdue deadlines panel (red theme)
- Upcoming deadlines panel (30-day window)
- Create deadline form (title, date, type, description)
- Type categories: REGULATORY, FILING, REVIEW, AUDIT, REPORTING
- Toast notifications on create

## Webhook Events

Configured via `/api/v1/webhooks/subscribe`:

```json
{
  "url": "https://mypsp.com/webhooks/hokeka",
  "events": ["alert.created", "case.updated", "transaction.flagged"],
  "secret": "my-webhook-secret"
}
```

### Payload Format

```json
{
  "event": "alert.created",
  "timestamp": "2026-06-29T14:32:15Z",
  "pspId": 7,
  "data": {
    "alertId": 445566,
    "txnId": 1048576,
    "score": 87.3,
    "riskLevel": "HIGH",
    "action": "HOLD",
    "reason": "Velocity threshold exceeded",
    "triggeredRules": ["R-2", "R-7"]
  }
}
```