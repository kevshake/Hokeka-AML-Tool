# Fraud Detection

## Overview

The fraud detection pipeline combines machine learning (XGBoost, DL4J), rule engines (Drools 8, Easy Rules, SpEL), and real-time feature extraction to assess 80+ risk signals per transaction.

## Detection Dimensions

### 1. Device Risk Assessment
- Device blacklist check
- New device detection (first seen flag)
- Device counter increment
- Aerospike shadow write for cross-session tracking

### 2. IP Risk Assessment
- IP blacklist check
- GeoIP lookup (country, city, ISP)
- High-risk country match
- VPN/proxy detection
- Aerospike shadow write

### 3. Velocity Risk Assessment
- Sliding window: transaction count in last N minutes/hours
- Velocity rules from DB (per PSP, per merchant)
- Aerospike shadow write for real-time increment
- Configurable thresholds per service type

### 4. Behavioral Risk Assessment
- Amount vs historical baseline (30-day rolling average)
- Unusual time of day detection
- MCC change frequency
- Geographic distance from normal patterns

### 5. CRA (Customer Risk Assessment)
5-dimension weighted score:

| Dimension | Weight |
|---|---|
| Amount Risk | 20 pts |
| KRS (Know Risk Score) | 25 pts |
| TRS (Transaction Risk Score) | 25 pts |
| Geographic Risk | 15 pts |
| Velocity Risk | 15 pts |

Rule Engines

The system supports three coexisting rule engines:

| Engine | Purpose | Use Case |
|---|---|---|
| Drools 8 | Complex event processing | Multi-condition AML rules, scoring |
| Easy Rules | Simple conditional rules | Quick configurable business rules |
| SpEL | Spring Expression Language | Dynamic threshold evaluation |

AI Rule Generation

Natural-language rule creation via AI:
```
User prompt → AiRuleGeneratorService → Anthropic Claude → RuleDefinition
```

Available via the **RulesGeneration** page with preview before save.

## ML Models

### XGBoost
- Real-time scoring engine
- Trained on historical transactions with labeled outcomes
- ~80 features, ensemble of 500 trees
- Thresholds: BLOCK ≥0.8, HOLD ≥0.5, ALERT ≥0.3

### DL4J (Deep Learning for Java)
- Deep neural network for anomaly detection
- Autoencoder architecture
- Triggered only on suspicious transactions (reduces latency)
- Detects novel fraud patterns not in training data

## Pages

**RulesGenerationPage**: Full CRUD for AML rules, velocity rules, risk thresholds. AI rule generation with preview. Rule effectiveness tracking.

**RuleEditorModal**: Individual rule editing with conditions, actions, and enable/disable controls.