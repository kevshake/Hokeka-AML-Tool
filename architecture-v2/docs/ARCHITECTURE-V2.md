# High-Performance AML Architecture V2

## Executive Summary

This document describes the redesigned high-performance architecture for the Fraud Detector AML system, targeting:

- **Throughput**: 10,000+ TPS
- **Decision Latency**: < 100ms (p99)
- **Feature Retrieval**: < 5ms
- **Data Retention**: 7 years (audit), 1 year (cases)

## Core Design Principles

### 1. Separate Hot and Cold Paths

| Path | Purpose | Latency Target | Storage |
|------|---------|----------------|---------|
| **Hot** | Real-time decisions | < 100ms | Redis + PostgreSQL |
| **Warm** | Feature computation | < 1s | Kafka + PostgreSQL |
| **Cold** | Analytics & audit | Seconds-Minutes | ClickHouse + PostgreSQL |

### 2. Pre-computed Features

**Anti-Pattern (Old)**:
```sql
-- ❌ Expensive query per transaction
SELECT COUNT(*), SUM(amount) 
FROM transactions 
WHERE customer_id = ? AND txn_ts > NOW() - INTERVAL '1 hour'
```

**New Pattern**:
```java
// ✅ O(1) cache lookup
CustomerFeatures features = featureCache.get(customerId);
int count1h = features.getTxCount1h();
long volume24h = features.getTxVolume24h();
```

### 3. Event-Driven Architecture

```
┌─────────────────┐     ┌─────────────┐     ┌─────────────────┐
│ Transaction     │────▶│  Kafka      │────▶│ Feature Engine  │
│ Source (POS)    │     │  (raw)      │     │ (Async compute) │
└─────────────────┘     └─────────────┘     └─────────────────┘
                                                      │
                           ┌──────────────────────────┘
                           ▼
                    ┌─────────────┐
                    │   Redis     │◀──── Rule Engine (Hot Path)
                    │  (Cache)    │      O(1) feature lookup
                    └─────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │ PostgreSQL  │
                    │ (Partitioned)
                    └─────────────┘
```

## Data Architecture Layers

### Layer 1: Ingestion (Kafka)

**Topics**:

| Topic | Partitions | Key | Retention | Purpose |
|-------|------------|-----|-----------|---------|
| `transactions.raw` | 32 | customer_id | 7 days | Entry point |
| `transactions.enriched` | 32 | customer_id | 7 days | With profile |
| `features.updates` | 32 | customer_id | 1 day | Feature changes |
| `transactions.decisions` | 32 | customer_id | 30 days | Final decisions |
| `alerts.generated` | 16 | alert_id | 90 days | Suspicious activity |
| `cases.events` | 8 | case_id | 1 year | Case lifecycle |
| `transactions.audit` | 32 | customer_id | 7 years | Immutable audit |

**Partitioning Strategy**:
- Key: `customer_id` (ensures per-customer ordering)
- Count: 32 partitions (scales to 64/128)
- Formula: `partitions = TPS / 500` (target 500 TPS/partition)

### Layer 2: Real-Time Store (Hot Path)

**Redis**:
- Feature cache: O(1) access
- Velocity counters: Sliding window (sorted sets)
- Blacklists: Set membership
- TTL strategy:
  - Features: 7 days
  - Velocity: 1-24 hours
  - Session: Minutes

**Key Patterns**:
```
aml:customer:{id}:features      → CustomerFeatures object
aml:customer:{id}:tx:timestamps → Sorted set of tx timestamps
aml:customer:{id}:velocity:1h   → Counter with expiry
aml:blacklist:merchant          → Set of blacklisted merchants
```

### Layer 3: OLTP Store (PostgreSQL)

**Partitioned Tables**:

```sql
-- Main transactions table (range partitioned by month)
CREATE TABLE transactions (...) PARTITION BY RANGE (txn_ts);
CREATE TABLE transactions_2026_03 PARTITION OF transactions ...;
CREATE TABLE transactions_2026_04 PARTITION OF transactions ...;
```

**Indexes** (Critical for Performance):
```sql
-- Primary access pattern
CREATE INDEX idx_txn_customer_ts ON transactions (customer_id, txn_ts DESC);

-- Time-based queries
CREATE INDEX idx_txn_timestamp ON transactions (txn_ts);

-- Merchant analysis
CREATE INDEX idx_txn_merchant ON transactions (merchant_id);
```

**Customer Features Table**:
```sql
CREATE TABLE customer_features (
    customer_id VARCHAR(64) PRIMARY KEY,
    tx_count_1h INTEGER,      -- Pre-computed
    tx_count_24h INTEGER,
    tx_volume_24h BIGINT,
    risk_score INTEGER,
    countries_last_24h TEXT,  -- JSON array
    last_tx_timestamp TIMESTAMP,
    updated_at TIMESTAMP
);
```

### Layer 4: Analytics Store (ClickHouse)

For complex analytical queries:
```sql
-- Pattern detection on billions of rows
SELECT customer_id, SUM(amount)
FROM transactions
WHERE timestamp > now() - INTERVAL 7 DAY
GROUP BY customer_id
HAVING SUM(amount) > 1000000;
```

## Transaction Processing Flow

### Step 1: Ingestion (0-10ms)
```
POS/ATM → API Gateway → Kafka (transactions.raw)
```

### Step 2: Enrichment (10-30ms)
```
Consumer: transactions.raw
- Add customer profile
- Add merchant risk
- Produce to: transactions.enriched
```

### Step 3: Rule Evaluation (30-80ms)
```
Consumer: transactions.enriched
- Fetch features from Redis (O(1))
- Evaluate rules (in-memory)
- Make decision
- Produce to: transactions.decisions
```

### Step 4: Feature Update (Async)
```
Consumer: transactions.raw
- Update Redis counters
- Compute new features
- Update customer_features table
- Produce to: features.updates
```

### Step 5: Persistence (Async)
```
Consumer: transactions.decisions
- Write to PostgreSQL
- Write to ClickHouse (for analytics)
- Write to transactions.audit
```

## Performance Targets

| Metric | Target | Current (Old) |
|--------|--------|---------------|
| Decision latency (p50) | 50ms | 500ms |
| Decision latency (p99) | 100ms | 2000ms |
| Throughput | 10k TPS | 1k TPS |
| Feature lookup | < 1ms | 50-200ms |
| Query time (30d range) | < 100ms | 5-10s |

## Scalability

### Horizontal Scaling

**Kafka**:
- Add brokers → increase throughput
- Add partitions → increase parallelism

**Consumers**:
- Rule Engine: 3+ replicas (CPU-bound)
- Feature Engine: 2+ replicas (I/O-bound)

**Redis**:
- Redis Cluster for > 1TB data
- Read replicas for scaling reads

**PostgreSQL**:
- Read replicas for queries
- Partition pruning for time-range queries

### Vertical Scaling

| Service | Baseline | High Load |
|---------|----------|-----------|
| Backend | 2 CPU, 4GB | 8 CPU, 16GB |
| Kafka | 2 CPU, 4GB | 8 CPU, 16GB |
| Redis | 1 CPU, 2GB | 4 CPU, 16GB |
| PostgreSQL | 2 CPU, 4GB | 8 CPU, 32GB |

## Deployment

### Quick Start
```bash
cd /home/ubuntu/fraud-detector/architecture-v2/infra

# Start infrastructure
docker-compose -f docker-compose.high-perf.yml up -d

# Verify services
docker-compose -f docker-compose.high-perf.yml ps
```

### Access Points

| Service | URL | Credentials |
|---------|-----|-------------|
| Kafka UI | http://localhost:8085 | - |
| Redis Commander | http://localhost:8086 | - |
| Grafana | http://localhost:3000 | admin/admin_secure_2024 |
| Prometheus | http://localhost:9090 | - |
| Backend API | http://localhost:8080 | - |

## Migration from V1

### Phase 1: Dual Write
- Deploy new infrastructure alongside old
- Write to both systems
- Validate data consistency

### Phase 2: Feature Warmup
- Backfill customer_features table
- Pre-populate Redis cache
- Warm up Kafka topics

### Phase 3: Traffic Shift
- Route 10% traffic → new system
- Monitor latencies/errors
- Gradually increase to 100%

### Phase 4: Decommission
- Stop writes to old system
- Archive old data
- Decommission old infrastructure

## Monitoring

### Key Metrics

**Latency**:
- `aml_decision_latency_ms` (histogram)
- `aml_feature_lookup_ms` (histogram)
- `aml_kafka_consumer_lag` (gauge)

**Throughput**:
- `aml_transactions_per_second` (counter)
- `aml_kafka_messages_in` (counter)

**Errors**:
- `aml_decision_errors_total` (counter)
- `aml_cache_miss_ratio` (gauge)

**Business**:
- `aml_alerts_generated` (counter)
- `aml_false_positive_rate` (gauge)

## Troubleshooting

### High Decision Latency
1. Check Redis latency: `redis-cli --latency`
2. Check consumer lag: Kafka UI
3. Check PostgreSQL: slow query log

### Cache Misses
1. Verify feature engine is running
2. Check Kafka topic: `features.updates`
3. Verify Redis memory: `INFO memory`

### Database Performance
1. Check partition pruning: `EXPLAIN ANALYZE`
2. Verify indexes: `SELECT * FROM pg_indexes`
3. Check bloat: `pg_stat_user_tables`

## Security Considerations

1. **Encryption**:
   - Kafka: TLS between brokers
   - Redis: AUTH password
   - PostgreSQL: SSL connections

2. **Access Control**:
   - Service accounts per component
   - Least privilege principle

3. **Audit**:
   - All decisions logged to `transactions.audit`
   - 7-year retention for compliance

## Cost Optimization

| Component | Cost Driver | Optimization |
|-----------|-------------|--------------|
| Kafka | Storage (retention) | Tiered storage, compact topics |
| Redis | Memory | TTL, eviction policy (LRU) |
| PostgreSQL | Storage | Partition dropping, compression |
| ClickHouse | Storage | Column compression, TTL |

## Conclusion

This architecture achieves high performance through:

1. **Separation of concerns** (hot/warm/cold paths)
2. **Pre-computed features** (O(1) lookups)
3. **Event-driven processing** (async, decoupled)
4. **Partitioned storage** (efficient time-series queries)
5. **Horizontal scalability** (stateless services)

The result is a system capable of handling 10k+ TPS with sub-100ms decision latency, while maintaining analytical accuracy for AML compliance.
