# HOKEKA Environment Configuration
# Central configuration for all environments

# ============================================
# PORT ASSIGNMENTS (Distinct for each environment)
# ============================================

## LIVE / PRODUCTION (main branch)
# API Backend: 2637
# Dashboard Frontend: 8088
# Website: 80/443 (nginx)
# PostgreSQL: 25432
# Aerospike: 3000

## PRE-PRODUCTION (preprod branch)
# API Backend: 2639
# Dashboard Frontend: 8089
# Website: 8090 (optional separate port)
# PostgreSQL: 25433
# Aerospike: 3001

## TEST / UAT (test branch)
# API Backend: 2638
# Dashboard Frontend: 5174 (Vite dev) / 8091 (production build)
# Website: 8092 (optional)
# PostgreSQL: 15432 (already exists)
# Aerospike: 3002

# ============================================
# DATABASE ASSIGNMENTS
# ============================================

## LIVE Database
# Container: fd-prod-postgres
# External Port: 25432
# Internal Port: 5432
# Database: fraud_detector_prod
# User: fd_prod

## PRE-PROD Database  
# Container: fd-preprod-postgres
# External Port: 25433
# Internal Port: 5432
# Database: fraud_detector_preprod
# User: fd_preprod

## TEST/UAT Database
# Container: fraud-detector-postgres-test
# External Port: 15432
# Internal Port: 5432
# Database: fraud_detector_test
# User: fd_test

# ============================================
# GIT BRANCH STRATEGY
# ============================================
# main     -> LIVE Production (deployed to aml.hokeka.com)
# preprod  -> Pre-Production (deployed to preprod-aml.hokeka.com)
# test     -> Test/UAT (deployed to testaml.hokeka.com)
# feature/* -> Feature branches (merge to test first)
