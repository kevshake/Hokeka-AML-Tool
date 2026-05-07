# Architecture V2 Implementation Checklist

## Phase 1: Infrastructure Setup ✅

- [x] Kafka Topics Configuration (8 topics)
- [x] Redis Configuration
- [x] PostgreSQL Partitioned Schema
- [x] Docker Compose for Infrastructure
- [x] Architecture Documentation

## Phase 2: Backend Implementation

### 2.1 Entity Classes
- [ ] Copy `CustomerFeatures.java` to source-code/BACKEND
- [ ] Update `TransactionEntity.java` with partitioning support
- [ ] Add `PartitionedTransactionEntity.java`
- [ ] Update `Alert.java` with new fields if needed

### 2.2 Configuration
- [ ] Add `RedisConfig.java` to config package
- [ ] Update `KafkaTopicsConfig.java` with new topics
- [ ] Add Redis dependency to pom.xml

### 2.3 Services
- [ ] Add `FeatureCacheService.java`
- [ ] Create `FeatureEngineService.java` (Kafka consumer)
- [ ] Create `RuleEngineService.java` (stream processor)
- [ ] Update existing services to use feature cache

### 2.4 Repositories
- [ ] Create `CustomerFeaturesRepository.java`
- [ ] Add queries for feature computation
- [ ] Create `PartitionedTransactionRepository.java`

### 2.5 Dockerfiles
- [ ] Create `Dockerfile.hp` (high-performance backend)
- [ ] Create `Dockerfile.feature-engine`
- [ ] Create `Dockerfile.rule-engine`

## Phase 3: Database Migration

### 3.1 Schema Setup
- [ ] Run `schema-partitioned.sql` on PostgreSQL
- [ ] Create initial partitions for current month + 3 months
- [ ] Set up partition maintenance cron jobs

### 3.2 Data Migration
- [ ] Backfill `customer_features` table from existing transactions
- [ ] Migrate historical transactions to partitioned tables
- [ ] Verify data consistency

### 3.3 Indexes
- [ ] Verify all indexes are created
- [ ] Run `ANALYZE` on all tables
- [ ] Test query performance

## Phase 4: Testing

### 4.1 Unit Tests
- [ ] Test `FeatureCacheService`
- [ ] Test partition pruning
- [ ] Test Kafka producers/consumers

### 4.2 Integration Tests
- [ ] End-to-end transaction flow
- [ ] Feature computation accuracy
- [ ] Cache hit/miss ratios

### 4.3 Performance Tests
- [ ] Load test: 10k TPS target
- [ ] Latency test: p50, p95, p99
- [ ] Stress test: 2x normal load

### 4.4 Chaos Tests
- [ ] Redis failure (fallback to DB)
- [ ] Kafka broker failure
- [ ] PostgreSQL replica promotion

## Phase 5: Deployment

### 5.1 Infrastructure
- [ ] Deploy Kafka cluster
- [ ] Deploy Redis cluster
- [ ] Deploy PostgreSQL with partitions
- [ ] Deploy ClickHouse (optional)

### 5.2 Application
- [ ] Deploy Feature Engine (2+ replicas)
- [ ] Deploy Rule Engine (3+ replicas)
- [ ] Deploy Backend API (2+ replicas)

### 5.3 Monitoring
- [ ] Set up Prometheus metrics
- [ ] Configure Grafana dashboards
- [ ] Set up alerting (PagerDuty/Slack)

## Phase 6: Migration from V1

### 6.1 Dual Write
- [ ] Enable dual write to both V1 and V2
- [ ] Monitor data consistency
- [ ] Fix any discrepancies

### 6.2 Cache Warmup
- [ ] Pre-populate Redis with active customers
- [ ] Backfill customer_features table
- [ ] Verify cache hit rate > 95%

### 6.3 Traffic Shifting
- [ ] 10% traffic → V2 (1 day)
- [ ] 25% traffic → V2 (2 days)
- [ ] 50% traffic → V2 (2 days)
- [ ] 100% traffic → V2

### 6.4 Decommission
- [ ] Stop writes to V1
- [ ] Archive V1 data
- [ ] Decommission V1 infrastructure

## Phase 7: Optimization

### 7.1 Performance Tuning
- [ ] JVM GC tuning
- [ ] Kafka batch sizes
- [ ] Redis eviction policies
- [ ] PostgreSQL connection pooling

### 7.2 Cost Optimization
- [ ] Right-size instances
- [ ] Implement data retention policies
- [ ] Optimize storage costs

### 7.3 Documentation
- [ ] Update API documentation
- [ ] Create runbooks
- [ ] Document troubleshooting procedures

## Quick Commands

```bash
# Start infrastructure
cd architecture-v2/infra
docker-compose -f docker-compose.high-perf.yml up -d

# Check status
docker-compose -f docker-compose.high-perf.yml ps

# View logs
docker-compose -f docker-compose.high-perf.yml logs -f [service]

# Scale services
docker-compose -f docker-compose.high-perf.yml up -d --scale rule-engine=5

# Stop everything
docker-compose -f docker-compose.high-perf.yml down
```

## Key Metrics to Monitor

| Metric | Target | Alert Threshold |
|--------|--------|-----------------|
| Decision latency (p99) | < 100ms | > 200ms |
| Feature lookup (p99) | < 5ms | > 10ms |
| Kafka consumer lag | < 1000 | > 5000 |
| Cache hit ratio | > 95% | < 90% |
| Error rate | < 0.1% | > 0.5% |
| CPU utilization | < 70% | > 85% |
| Memory utilization | < 80% | > 90% |

## Rollback Plan

If issues occur during migration:

1. **Immediate** (if critical):
   ```bash
   # Route 100% traffic back to V1
   kubectl set env deployment/gateway ROUTE_TO_V2=false
   ```

2. **Within 1 hour**:
   - Analyze root cause
   - Fix and redeploy
   - Resume migration

3. **Data consistency**:
   - Reconcile V1 and V2 data
   - Backfill any missing records

## Success Criteria

- [ ] 10,000 TPS sustained throughput
- [ ] < 100ms p99 decision latency
- [ ] < 5ms p99 feature lookup
- [ ] 99.99% uptime
- [ ] Zero data loss
- [ ] < 0.1% false positive rate change
