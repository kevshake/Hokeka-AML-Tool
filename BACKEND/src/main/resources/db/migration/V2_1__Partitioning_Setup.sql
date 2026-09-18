-- Partitioning Setup for Transactions and Screening Results

-- 1. Partition Transactions by Date (Monthly)
CREATE TABLE IF NOT EXISTS transactions_partitioned (
    transaction_id VARCHAR(255) NOT NULL,
    amount DECIMAL(19, 2),
    currency VARCHAR(3),
    timestamp TIMESTAMP,
    merchant_id VARCHAR(255),
    status VARCHAR(50),
    PRIMARY KEY (transaction_id, timestamp)
) PARTITION BY RANGE (timestamp);

-- Create partitions for 2024 (Example)
CREATE TABLE IF NOT EXISTS transactions_2024_01 PARTITION OF transactions_partitioned
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
CREATE TABLE IF NOT EXISTS transactions_2024_02 PARTITION OF transactions_partitioned
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');

-- 2. Partition Screening Results by Date (Monthly)
CREATE TABLE IF NOT EXISTS merchant_screening_results_partitioned (
    id BIGSERIAL, -- Changed from UUID to BIGSERIAL for partitioning efficiency
    merchant_id BIGINT,
    screening_type VARCHAR(50),
    screening_status VARCHAR(50),
    match_score DECIMAL(5, 2),
    match_count INTEGER,
    match_details JSONB,
    screening_provider VARCHAR(50),
    screened_at TIMESTAMP,
    screened_by VARCHAR(100),
    PRIMARY KEY (id, screened_at)
) PARTITION BY RANGE (screened_at);

-- Create partitions
CREATE TABLE IF NOT EXISTS screening_results_2024_01 PARTITION OF merchant_screening_results_partitioned
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
CREATE TABLE IF NOT EXISTS screening_results_2024_02 PARTITION OF merchant_screening_results_partitioned
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');

