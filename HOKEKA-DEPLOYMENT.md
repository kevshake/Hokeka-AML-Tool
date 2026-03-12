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

### 3. Test Environment (Time-Based Access)

```bash
# Copy and configure environment
cp .env.example .env.test
# Edit .env.test with test credentials

# Start test environment
docker compose -f docker-compose.test.yml up -d --build

# Access:
# - Frontend: http://localhost:9080
# - API: http://localhost:9080/api
# - PostgreSQL: localhost:25432
# ⚠️  ONLY ACCESSIBLE 9am-11am EAT (6am-8am UTC)
```

### 4. Production Environment

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

| Feature | Development | Test | Preprod | Production |
|---------|-------------|------|---------|------------|
| Frontend Port | 5173 | 9080 | 8080 | 80/443 |
| Hot Reload | ✅ Yes | ❌ No | ❌ No | ❌ No |
| PostgreSQL Port | 5432 | 25432 | 15432 | Internal only |
| Aerospike Port | 3000 | - | 13000 | Internal only |
| SSL | ❌ No | ❌ No | Optional | ✅ Required |
| **Access Control** | **None** | **Open (Time later)** | **IP Whitelist** | **VPN Only** |
| Resource Limits | ❌ No | ❌ No | ❌ No | ✅ Yes |
| Logging Level | DEBUG | INFO | INFO | WARN |
| Database Names | fraud_detector_dev | fraud_detector_test | fraud_detector_preprod | fraud_detector |

## Network Isolation

Each environment has its own Docker network:
- **Dev:** 172.21.0.0/16
- **Test:** 172.24.0.0/16
- **Preprod:** 172.22.0.0/16
- **Production:** 172.23.0.0/16

This ensures complete isolation between environments.

## Access Control Configuration

### Preprod Environment - IP Whitelist

Preprod requires specific IP addresses to be whitelisted.

**Configure in `nginx-preprod.conf`:**

```nginx
# Add allowed IPs in the server block:
allow 192.168.1.0/24;  # Office network
allow 10.0.0.0/8;      # VPN network
allow 72.62.133.45;    # Specific IP
deny all;              # Deny all others
```

**Or create `allowed-ips.conf`:**
```bash
cat > allowed-ips.conf << 'EOF'
allow 192.168.1.0/24;
allow 10.0.0.0/8;
deny all;
EOF
```

Then mount it in docker-compose:
```yaml
volumes:
  - ./allowed-ips.conf:/etc/nginx/allowed-ips.conf:ro
```

### Test Environment - Time-Based Access (Currently Open)

Test environment time restriction is **DISABLED** for now. It will be enabled when moving to preprod.

**Currently:** Open access 24/7  
**Future:** Only 9am-11am EAT (6am-8am UTC) - configured in `nginx-test.conf`

**To enable time restriction later:**
1. Edit `nginx-test.conf`
2. Uncomment the time control section
3. Restart: `docker compose -f docker-compose.test.yml restart nginx-test`

**Start test environment:**
```bash
docker compose -f docker-compose.test.yml up -d --build
# Access: http://localhost:9080 (open access for now)
```

### Production Environment - VPN Only

Production requires VPN access. Configure at multiple levels:

**1. Firewall Level (Recommended):**
```bash
# Allow only VPN subnet
iptables -A INPUT -p tcp --dport 80 -s 10.0.0.0/8 -j ACCEPT
iptables -A INPUT -p tcp --dport 443 -s 10.0.0.0/8 -j ACCEPT
iptables -A INPUT -p tcp --dport 80 -j DROP
iptables -A INPUT -p tcp --dport 443 -j DROP
```

**2. Nginx Level:**
Edit `nginx-prod.conf` and uncomment:
```nginx
allow 10.0.0.0/8;      # VPN network
allow 172.16.0.0/12;   # Private VPN range
deny all;
```

**3. Cloud Provider Level:**
Configure security groups to only allow VPN IPs:
- AWS: Security Group inbound rules
- Azure: Network Security Group rules
- GCP: Firewall rules

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
