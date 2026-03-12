# HOKEKA Workspace: Fraud Detector

## Repository
**URL:** https://github.com/kevshake/Fraud_Detector  
**Local Path:** `/root/.openclaw/workspace/fraud-detector`

## Project Overview
AML Fraud Detection System - Enterprise-grade Anti-Money Laundering platform with real-time transaction monitoring, ML-powered risk scoring, and case management.

## Technology Stack
- **Backend:** Java 17, Spring Boot 3.2.0
- **Frontend:** React 18, TypeScript 5.2
- **Architecture:** Multi-tenant, microservices-ready

## Structure
```
fraud-detector/
├── BACKEND/          # Java Spring Boot API
├── FRONTEND/         # React TypeScript UI
├── docs/             # Documentation
└── README.md         # Full project docs
```

## Auto-Push Configuration

**Status:** ✅ Enabled  
**Frequency:** Every 5 minutes  
**Script:** `.auto-push.sh`

Changes made in this folder are automatically committed and pushed to git every 5 minutes.

### How It Works
1. Any agent/developer works in `/root/.openclaw/workspace/fraud-detector`
2. Changes are automatically detected
3. Auto-committed with timestamp: `Auto-commit: YYYY-MM-DD HH:MM:SS UTC`
4. Pushed to origin/main

### Manual Push
If you need to push immediately:
```bash
cd /root/.openclaw/workspace/fraud-detector
./.auto-push.sh
```

### View Push Log
```bash
cat /root/.openclaw/workspace/fraud-detector/.auto-push.log
```

## Development Workflow

This project uses the HOKEKA three-environment strategy:
- **Development:** `dev-fraud.hokeka.com` (for active development)
- **Preprod:** `preprod-fraud.hokeka.com` (for QA)
- **Production:** `fraud.hokeka.com` (live)

### Making Changes
1. Developers work in this local repo
2. Changes auto-push to git every 5 minutes
3. To deploy: request promotion through Technical Head

## Owner
**Lead:** Kevin Njenga  
**Organization:** HOKEKA
