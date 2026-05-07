-- Architecture V2 Database Initialization
-- Creates schemas and extensions needed for high-performance processing

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Create schemas
CREATE SCHEMA IF NOT EXISTS features;
CREATE SCHEMA IF NOT EXISTS rules;
CREATE SCHEMA IF NOT EXISTS analytics;

-- Create application user (if not exists)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'fraud_app') THEN
        CREATE USER fraud_app WITH PASSWORD 'fraud_app_2024';
    END IF;
END
$$;

-- Grant privileges
GRANT ALL PRIVILEGES ON SCHEMA public TO fraud_app;
GRANT ALL PRIVILEGES ON SCHEMA features TO fraud_app;
GRANT ALL PRIVILEGES ON SCHEMA rules TO fraud_app;
GRANT ALL PRIVILEGES ON SCHEMA analytics TO fraud_app;

-- Create partition management function
CREATE OR REPLACE FUNCTION create_monthly_partition(
    p_table_name TEXT,
    p_partition_date DATE
) RETURNS TEXT AS $$
DECLARE
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
BEGIN
    partition_name := p_table_name || '_' || to_char(p_partition_date, 'YYYY_MM');
    start_date := date_trunc('month', p_partition_date);
    end_date := start_date + INTERVAL '1 month';
    
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
        partition_name,
        p_table_name,
        start_date,
        end_date
    );
    
    RETURN partition_name;
END;
$$ LANGUAGE plpgsql;

-- Note: Main tables with partitions are defined in schema-partitioned.sql
