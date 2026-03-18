# =============================================================================
# FRAUD DETECTOR - PRODUCTION DEPLOYMENT GUIDE
# For External Access via hokeka.com
# =============================================================================

## Overview

This guide covers deploying the Fraud Detector backend for external internet access
via api.hokeka.com or hokeka.com/api with SSL/HTTPS.

## Architecture

```
Internet 
    |
    v
[Cloudflare/ DNS] --> [nginx :443] --> [Spring Boot :2637]
                           |                |
                     SSL Termination    PostgreSQL :5432
                     Rate Limiting
                     Security Headers
```

## Quick Answers to Kevin's Questions

### 1. Spring Boot Configuration for Production?

YES - Create a new `production` profile. Do NOT use `testenv` for external access!

Key differences from `testenv`:
- `spring.jpa.hibernate.ddl-auto=validate` (NOT create-drop)
- Flyway enabled for migrations
- External services configured via environment variables
- Connection pooling tuned for production load
- Logging at WARN/INFO level (not DEBUG)

### 2. CORS Configuration for hokeka.com?

Created `WebMvcConfig.java` that allows:
- https://hokeka.com
- https://www.hokeka.com  
- https://api.hokeka.com
- https://app.hokeka.com

Configurable via `CORS_ALLOWED_ORIGINS` environment variable.

### 3. Security Considerations for External Access?

Implemented:
- SecurityHeadersFilter - Adds HSTS, CSP, X-Frame-Options, etc.
- ProductionRateLimitFilter - IP-based rate limiting (100 RPM general, 10 RPM auth)
- CORS with credentials support for authenticated requests
- Session cookies with Secure, HttpOnly, SameSite=Strict
- CSRF protection enabled

Additional nginx-level:
- Rate limiting zones
- SSL/TLS 1.2+ with strong ciphers
- Request size limits
- Connection limits

### 4. Application Properties for Production Profile?

Created `application-production.properties` with:
- Server binding to 0.0.0.0 for external access
- Forwarded headers support for nginx proxy
- HikariCP pool: 50 max, 10 min idle
- Tomcat: 400 max threads, 2000 max connections
- All secrets via environment variables (no hardcoded values)

### 5. Database Connection Pooling for External Load?

HikariCP configuration:
```properties
spring.datasource.hikari.maximum-pool-size=50
spring.datasource.hikari.minimum-idle=10
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.connection-timeout=20000
```

Monitor with: `SELECT * FROM pg_stat_activity;`

### 6. Should We Use a New 'production' Profile?

YES! Critical reasons:
- `testenv` uses `create-drop` which destroys data on restart
- `testenv` has debug logging (security risk)
- `testenv` disables Flyway (no migrations)
- `testenv` uses weak JWT secrets
- Production needs proper CORS, rate limiting, security headers

## Deployment Steps

### Step 1: Environment Variables

Create `/opt/fraud-detector/.env`:

```bash
# Database
export DATABASE_URL="jdbc:postgresql://localhost:5432/fraud_detector_prod"
export DATABASE_USERNAME="fraud_detector_prod"
export DATABASE_PASSWORD="<strong-password-here>"
export DB_MAX_POOL_SIZE="50"

# JWT Security
export JWT_SECRET="<generate-256-bit-secret-here>"
export JWT_EXPIRATION_HOURS="24"
export JWT_REFRESH_EXPIRATION_DAYS="7"

# Encryption
export ENCRYPTION_KEY="<32-byte-encryption-key>"
export AUDIT_HMAC_KEY="<32-byte-hmac-key>"

# CORS
export CORS_ALLOWED_ORIGINS="https://hokeka.com,https://www.hokeka.com,https://api.hokeka.com"

# Rate Limiting
export RATE_LIMIT_ENABLED="true"
export RATE_LIMIT_RPM="100"

# Server
export SERVER_PORT="2637"
export LOG_FILE_PATH="/var/log/fraud-detector/application.log"
```

### Step 2: Database Setup

```sql
-- Create production database
CREATE DATABASE fraud_detector_prod;
CREATE USER fraud_detector_prod WITH PASSWORD 'strong-password';
GRANT ALL PRIVILEGES ON DATABASE fraud_detector_prod TO fraud_detector_prod;

-- Set connection limits (PostgreSQL)
ALTER SYSTEM SET max_connections = 200;
```

### Step 3: SSL Certificate (Let's Encrypt)

```bash
# Install certbot
sudo apt install certbot python3-certbot-nginx

# Obtain certificate
sudo certbot --nginx -d hokeka.com -d www.hokeka.com -d api.hokeka.com

# Auto-renewal test
sudo certbot renew --dry-run
```

### Step 4: nginx Configuration

```bash
# Copy nginx config
sudo cp nginx/fraud-detector-api.conf /etc/nginx/sites-available/
sudo ln -s /etc/nginx/sites-available/fraud-detector-api.conf /etc/nginx/sites-enabled/

# Test and reload
sudo nginx -t
sudo systemctl reload nginx
```

### Step 5: Build and Deploy Application

```bash
# Build with production profile
cd /path/to/Fraud_Detector/BACKEND
mvn clean package -DskipTests

# Deploy
sudo mkdir -p /opt/fraud-detector
sudo cp target/aml-fraud-detector-1.0.0-SNAPSHOT.jar /opt/fraud-detector/

# Create systemd service
sudo tee /etc/systemd/system/fraud-detector.service > /dev/null <<EOF
[Unit]
Description=Fraud Detector API
After=network.target postgresql.service

[Service]
Type=simple
User=fraud-detector
Group=fraud-detector
WorkingDirectory=/opt/fraud-detector
EnvironmentFile=/opt/fraud-detector/.env
ExecStart=/usr/bin/java -Xms1g -Xmx2g -jar aml-fraud-detector-1.0.0-SNAPSHOT.jar --spring.profiles.active=production
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable fraud-detector
sudo systemctl start fraud-detector
```

### Step 6: Verify Deployment

```bash
# Check service status
sudo systemctl status fraud-detector

# Test health endpoint
curl https://api.hokeka.com/actuator/health

# Test API with auth
curl -X POST https://api.hokeka.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}'

# Check logs
sudo tail -f /var/log/fraud-detector/application.log
```

## Security Checklist

- [ ] All secrets stored in environment variables (not in code)
- [ ] JWT secret is 256-bit minimum
- [ ] PostgreSQL password is strong and unique
- [ ] SSL certificate is valid and auto-renews
- [ ] Firewall allows only 443 (and 80 for redirect)
- [ ] Rate limiting enabled and tested
- [ ] CORS origins restricted to hokeka.com domains
- [ ] Security headers present on all responses
- [ ] Database migrations run successfully (Flyway)
- [ ] Logging configured to detect attacks
- [ ] Monitoring alerts configured

## Monitoring

### Key Metrics to Watch

```bash
# Application metrics
curl https://api.hokeka.com/actuator/metrics

# Database connections
psql -c "SELECT count(*) FROM pg_stat_activity;"

# nginx access patterns
sudo tail -f /var/log/nginx/fraud-detector-access.log

# Application errors
sudo journalctl -u fraud-detector -f
```

### Recommended Alerts

- 5xx error rate > 1%
- Response time > 2 seconds (p95)
- Database connections > 80% of max
- Disk space > 85% full
- SSL certificate expiry < 7 days

## Rollback Plan

If issues occur:

```bash
# Stop new version
sudo systemctl stop fraud-detector

# Restore previous JAR (if backed up)
sudo cp /opt/fraud-detector/aml-fraud-detector-BACKUP.jar /opt/fraud-detector/aml-fraud-detector-1.0.0-SNAPSHOT.jar

# Restart
sudo systemctl start fraud-detector

# Or use testenv profile temporarily
sudo systemctl edit fraud-detector
# Change to: --spring.profiles.active=testenv
sudo systemctl restart fraud-detector
```

## External Testing Team Access

### API Documentation URL
```
https://api.hokeka.com/swagger-ui.html
```

### Test Credentials (Create for testing team)
```bash
# Create test user via database or admin endpoint
# Role: PSP_USER or TESTER (create if needed)
```

### API Base URL
```
https://api.hokeka.com/api/v1
```

### Example Requests
```bash
# Health check
curl https://api.hokeka.com/actuator/health

# Login
curl -X POST https://api.hokeka.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"tester","password":"test-pass"}' \
  -c cookies.txt

# Authenticated request
curl https://api.hokeka.com/api/v1/users/me \
  -b cookies.txt
```

## Troubleshooting

### CORS Errors
Check `CORS_ALLOWED_ORIGINS` includes exact origin (with https://)

### 502 Bad Gateway
- Check Spring Boot is running: `sudo systemctl status fraud-detector`
- Check port 2637 is listening: `sudo ss -tlnp | grep 2637`

### Rate Limiting Too Aggressive
Adjust in `.env`:
```bash
RATE_LIMIT_RPM=200
RATE_LIMIT_BURST=50
```

### Database Connection Errors
- Check PostgreSQL: `sudo systemctl status postgresql`
- Check connection pool settings
- Verify credentials in `.env`

## Files Created

1. `application-production.properties` - Production configuration
2. `WebMvcConfig.java` - CORS configuration
3. `SecurityHeadersFilter.java` - Security headers
4. `ProductionRateLimitFilter.java` - Rate limiting
5. `nginx/fraud-detector-api.conf` - nginx reverse proxy config
6. `DEPLOYMENT.md` - This file
