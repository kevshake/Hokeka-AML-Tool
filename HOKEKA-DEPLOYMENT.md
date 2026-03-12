# HOKEKA Fraud Detector - Three Environment Deployment Guide

## Quick Start

### 1. Development Environment

```bash
cd /root/.openclaw/workspace/fraud-detector

# Copy and configure environment
cp .env.example .env.dev
# Edit .env.dev with dev credentials

# Start development environment
docker compose -f docker-compose.dev.yml up --build

# Access:
# - Frontend: http://localhost:5173
# - API Direct: http://localhost:2637/api
# - PostgreSQL: localhost:5432
# - Aerospike: localhost:3000
```

### 2. Pre-Production / UAT Environment

```bash
# Copy and configure environment
cp .env.example .env.preprod
# Edit .env.preprod with preprod credentials (STRONG PASSWORDS!)

# Start preprod environment
docker compose -f docker-compose.preprod.yml up -d --build

# Access:
# - Frontend: http://localhost:8080
# - API: http://localhost:8080/api
# - PostgreSQL: localhost:15432 (different port)
```

### 3. Production Environment

```bash
# Copy and configure environment
cp .env.example .env.prod
# Edit .env.prod with production secrets

# Place SSL certificates in ssl/ directory
mkdir -p ssl
cp your-certificate.crt ssl/fraud.hokeka.com.crt
cp your-private-key.key ssl/fraud.hokeka.com.key

# Start production environment
docker compose -f docker-compose.prod.yml up -d --build

# Access:
# - Frontend: https://fraud.hokeka.com
# - API: https://fraud.hokeka.com/api
```

## Environment Differences

| Feature | Development | Preprod | Production |
|---------|-------------|---------|------------|
| Frontend Port | 5173 | 8080 | 80/443 |
| Hot Reload | ✅ Yes | ❌ No | ❌ No |
| PostgreSQL Port | 5432 | 15432 | Internal only |
| Aerospike Port | 3000 | 13000 | Internal only |
| SSL | ❌ No | Optional | ✅ Required |
| Resource Limits | ❌ No | ❌ No | ✅ Yes |
| Logging Level | DEBUG | INFO | WARN |
| Database Names | fraud_detector_dev | fraud_detector_preprod | fraud_detector |

## Network Isolation

Each environment has its own Docker network:
- **Dev:** 172.21.0.0/16
- **Preprod:** 172.22.0.0/16
- **Production:** 172.23.0.0/16

This ensures complete isolation between environments.

## SSL Certificate Setup

### For Production

1. Obtain SSL certificate (Let's Encrypt or purchased)
2. Place files in `ssl/` directory:
   - `ssl/fraud.hokeka.com.crt` (certificate)
   - `ssl/fraud.hokeka.com.key` (private key)
3. Uncomment HTTPS section in `nginx.conf`
4. Restart: `docker compose -f docker-compose.prod.yml restart nginx`

### Let's Encrypt (Recommended)

```bash
# Install certbot
apt install certbot

# Obtain certificate
certbot certonly --standalone -d fraud.hokeka.com

# Copy to project
cp /etc/letsencrypt/live/fraud.hokeka.com/fullchain.pem ssl/fraud.hokeka.com.crt
cp /etc/letsencrypt/live/fraud.hokeka.com/privkey.pem ssl/fraud.hokeka.com.key
```

## Environment Variables

### Required Variables (All Environments)

```bash
# Database
DATABASE_USERNAME=fraud_user
DATABASE_PASSWORD=strong_password_here

# Security (32-byte hex key)
SECURITY_ENCRYPTION_KEY=$(openssl rand -hex 32)

# CORS (comma-separated)
CORS_ALLOWED_ORIGINS=https://fraud.hokeka.com,https://app.hokeka.com
```

### Generating Secure Keys

```bash
# Generate 32-byte encryption key
openssl rand -hex 32

# Generate strong password
openssl rand -base64 32
```

## Maintenance

### Backup Database

```bash
# Development
docker exec fraud-detector-postgres-dev pg_dump -U fraud_user fraud_detector_dev > backup-dev.sql

# Production
docker exec fraud-detector-postgres-prod pg_dump -U fraud_user fraud_detector > backup-prod.sql
```

### View Logs

```bash
# All services
docker compose -f docker-compose.prod.yml logs -f

# Specific service
docker compose -f docker-compose.prod.yml logs -f backend-prod
```

### Update Deployment

```bash
# Pull latest code
git pull origin main

# Rebuild and restart
docker compose -f docker-compose.prod.yml down
docker compose -f docker-compose.prod.yml up -d --build
```

## Troubleshooting

### Port Already in Use

```bash
# Check what's using port 80
sudo lsof -i :80

# Stop nginx on host if running
sudo systemctl stop nginx

# Or change port in docker-compose file
```

### Database Connection Issues

```bash
# Check database health
docker compose -f docker-compose.prod.yml ps

# View database logs
docker compose -f docker-compose.prod.yml logs postgres-prod
```

### SSL Certificate Errors

```bash
# Verify certificate
openssl x509 -in ssl/fraud.hokeka.com.crt -text -noout

# Check file permissions (should be readable)
ls -la ssl/
```

## Migration from Single docker-compose.yml

If you were using the original `docker-compose.yml`:

```bash
# Stop old environment
docker compose down

# Switch to environment-specific files
# Development:
docker compose -f docker-compose.dev.yml up -d

# Or Production:
docker compose -f docker-compose.prod.yml up -d
```
