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

### Git Credentials Required

⚠️ **Push requires authentication.** The repo is private and needs credentials.

**To configure push access:**
1. Use GitHub Personal Access Token (PAT) with repo access
2. Or configure SSH key for the server
3. Or use git credential helper:
   ```bash
   git config credential.helper store
   git push origin main  # Enter credentials once, they'll be cached
   ```

### How It Works
1. Any agent/developer works in `/root/.openclaw/workspace/fraud-detector`
2. Changes are automatically detected every 5 minutes
3. Auto-committed with timestamp: `Auto-commit: YYYY-MM-DD HH:MM:SS UTC`
4. If credentials configured → pushed to origin/main
5. If no credentials → committed locally (you can push manually later)

### Manual Operations
```bash
cd /root/.openclaw/workspace/fraud-detector

# Check status
git status

# Push manually
git push origin main

# View auto-push log
cat .auto-push.log

# Run auto-push immediately
./.auto-push.sh
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
