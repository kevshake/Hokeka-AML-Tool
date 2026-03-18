# =============================================================================
# FRAUD DETECTOR - PRODUCTION SETUP SUMMARY FOR KEVIN (MD)
# =============================================================================

## Quick Answers

### Q1: What Spring Boot configuration needed for production?

**A: Use the new `production` profile created.** Key configs:

```yaml
Server:
  - Bind to 0.0.0.0 for external access
  - Port: 2637 (configurable)
  - Forwarded headers for nginx proxy
  
Database:
  - Hibernate ddl-auto: validate (NOT create-drop)
  - Flyway: ENABLED for migrations
  - HikariCP pool: 50 connections max
  
Security:
  - JWT from environment variable
  - Session cookies: Secure, HttpOnly, SameSite=Strict
  - CSRF enabled
```

### Q2: CORS configuration for hokeka.com domain?

**A: Configured in WebMvcConfig.java.** Allows:
- https://hokeka.com
- https://www.hokeka.com
- https://api.hokeka.com
- https://app.hokeka.com

Configurable via `CORS_ALLOWED_ORIGINS` env var.

### Q3: Security considerations for external access?

**A: Multiple layers implemented:**

1. **Application Level:**
   - SecurityHeadersFilter: HSTS, CSP, X-Frame-Options, etc.
   - ProductionRateLimitFilter: 100 req/min general, 10 req/min auth
   - CORS with credentials for authenticated requests
   - Session fixation protection

2. **nginx Level:**
   - SSL/TLS 1.2+ termination
   - Rate limiting zones
   - Request/connection limits
   - Security headers

3. **Network Level:**
   - Firewall: only 443 (and 80 for redirect)
   - PostgreSQL: localhost only

### Q4: Application properties for production profile?

**A: Created `application-production.properties` with:**
- All secrets via environment variables (no hardcoded values)
- Conservative thread pools for stability
- Structured logging to file
- Swagger UI disabled by default (enable with SWAGGER_UI_ENABLED=true)
- Health probes for monitoring

### Q5: Database connection pooling for external load?

**A: HikariCP tuned:**
```properties
max pool size: 50
min idle: 10
idle timeout: 10 min
max lifetime: 30 min
connection timeout: 20 sec
```

### Q6: Should we use a new 'production' profile instead of 'testenv'?

**A: ABSOLUTELY YES!** Critical reasons:

| testenv | production |
|---------|------------|
| `create-drop` (data loss!) | `validate` (keeps data) |
| Debug logging | Info/Warn logging |
| Weak JWT secrets | Strong env-based secrets |
| No Flyway migrations | Flyway enabled |
| No CORS restrictions | Proper CORS config |
| No rate limiting | Rate limits enabled |

## Deployment Architecture

```
Internet Users
      ↓
api.hokeka.com (or hokeka.com/api)
      ↓
   [nginx] ← SSL/TLS termination
      ↓
   :2637   ← Rate limiting, headers
      ↓
[Spring Boot]
      ↓
[PostgreSQL] ← Localhost only
```

## Required Environment Variables

```bash
# Critical - MUST set these
DATABASE_URL=jdbc:postgresql://localhost:5432/fraud_detector_prod
DATABASE_USERNAME=fraud_detector_prod
DATABASE_PASSWORD=<strong-unique-password>
JWT_SECRET=<256-bit-secret-minimum>
ENCRYPTION_KEY=<32-byte-key>
AUDIT_HMAC_KEY=<32-byte-key>

# Optional - defaults provided
CORS_ALLOWED_ORIGINS=https://hokeka.com,https://api.hokeka.com
RATE_LIMIT_RPM=100
SERVER_PORT=2637
```

## Files Created

```
BACKEND/
├── src/main/resources/
│   └── application-production.properties  ← NEW: Production config
├── src/main/java/com/posgateway/aml/config/
│   ├── WebMvcConfig.java                  ← NEW: CORS config
│   └── security/
│       ├── SecurityHeadersFilter.java     ← NEW: Security headers
│       └── ProductionRateLimitFilter.java ← NEW: Rate limiting
├── nginx/
│   └── fraud-detector-api.conf            ← NEW: nginx config
└── DEPLOYMENT.md                          ← NEW: Full deployment guide
```

## Recommended Domain Setup

**Option A: api.hokeka.com (RECOMMENDED)**
- Clean separation of API from main site
- Easier CORS management
- Better for CDN/api gateway in future

**Option B: hokeka.com/api**
- Single domain
- Path rewriting needed in nginx

Both configurations provided in nginx config.

## Next Steps

1. **Review** the DEPLOYMENT.md guide
2. **Set up** SSL certificates (Let's Encrypt)
3. **Configure** environment variables
4. **Deploy** with `production` profile
5. **Test** with external testing team

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Data loss from create-drop | Using production profile with validate |
| Unauthorized access | Rate limiting, JWT auth, CORS restrictions |
| DDoS | nginx rate limits, connection limits |
| Data breach | PII masking, encryption, security headers |
| Certificate expiry | Let's Encrypt auto-renewal |

## Approval Required For

- [ ] SSL certificate domain (api.hokeka.com vs hokeka.com/api)
- [ ] External testing team credentials
- [ ] Production database password
- [ ] JWT secret generation
- [ ] Firewall rules for port 443

---
**Prepared by:** Backend Developer (APIForge)  
**Date:** 2026-03-12  
**Status:** Ready for Technical Head review and deployment
