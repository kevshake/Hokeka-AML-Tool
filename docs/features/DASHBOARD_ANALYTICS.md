# Dashboard & Analytics

## Overview

The dashboard provides a real-time operational view of the entire AML platform. All KPIs are calculated from live database queries with a 4-minute automatic refresh.

## Dashboard Layout

```
┌────────────────────────────────────────────────────────┐
│ KPI Cards Row:                                         │
│  - Total Transactions (24h)                            │
│  - Open Alerts                                         │
│  - Fraud Detection Rate                                │
│  - Active Cases                                        │
│  - Screening Matches                                   │
│  - Compliance Health                                   │
├────────────────────────────────────────────────────────┤
│ ┌──────────────┐ ┌──────────────────┐ ┌──────────────┐│
│ │  Risk Gauge  │ │  Live Alert Queue│ │Investigation ││
│ │  (radial)    │ │  (latest alerts) │ │Cases (donut) ││
│ └──────────────┘ └──────────────────┘ └──────────────┘│
├────────────────────────────────────────────────────────┤
│ ┌──────────────────────┐ ┌──────────────────────────┐  │
│ │  Alert Trends Chart  │ │  Risk Heatmap            │  │
│ │  (line, 30 days)     │ │  (geo risk distribution) │  │
│ └──────────────────────┘ └──────────────────────────┘  │
├────────────────────────────────────────────────────────┤
│ ┌──────────────────┐ ┌─────────────┐ ┌───────────────┐│
│ │ Screening Results│ │Top Risk     │ │Compliance     ││
│ │ (today's stats)  │ │Merchants    │ │Health (radial)││
│ └──────────────────┘ └─────────────┘ └───────────────┘│
└────────────────────────────────────────────────────────┘
```

## Dashboard Refresh

- Automatic recalculation every 4 minutes (`0 */4 * * * *`)
- Configurable via `dashboard.analytics.refresh.enabled`
- Cached in DashboardCache singleton
- On-demand refresh via API

## KPI Definitions

| KPI | Source | Calculation |
|---|---|---|
| Total Transactions | transactions table | COUNT where created_at > 24h ago |
| Open Alerts | alerts table | COUNT where status = 'OPEN' |
| Fraud Detection Rate | alert_dispositions | TRUE_POSITIVE / (TRUE_POSITIVE + FALSE_POSITIVE) |
| Active Cases | compliance_cases | COUNT where status NOT IN (CLOSED_*) |
| Screening Matches | screening_log | COUNT where match_found = true AND today |
| Compliance Health | Multiple tables | Composite score: SLA breaches + overdue deadlines + case resolution rate |

## Analytics Pages

**AnalyticsPage**: Four tabs of analytical views:
- Transaction Overview (volume chart, decision distribution, KPI cards)
- Risk Trends (risk level over time)
- Fraud Metrics (detection rate, false positive rate, precision/recall/F1)
- Model Performance (AUC, drift, latency percentiles)

**RiskAnalyticsPage**: 
- Risk heatmap (customer/merchant toggle)
- Risk trends line chart (7/30/90/180 day periods)
- Color-coded risk cells (green/amber/red)

## Compliance Dashboard

**ComplianceDashboardService** provides:
- Open cases by status
- Cases by priority
- SLA deadlines approaching
- Breached SLA cases
- Unassigned cases
- High-risk transactions today
- Team workload distribution

**OperationalMetricsService** provides:
- Average investigation time
- SAR filing rate
- Alert-to-SAR conversion rate
- Investigator productivity (cases resolved/user)