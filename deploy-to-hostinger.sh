#!/bin/bash
# Deploy AML Fraud Detector to Hostinger VPS using Docker
# Usage: ./deploy-to-hostinger.sh <VPS_IP> <SSH_USER> <SSH_KEY_PATH>

set -e

VPS_IP=${1:-"YOUR_VPS_IP"}
SSH_USER=${2:-"root"}
SSH_KEY=${3:-"~/.ssh/id_rsa"}
REPO_DIR="/opt/aml-fraud-detector"

echo "=== Deploying to Hostinger VPS: $VPS_IP ==="

# 1. SSH and prepare the server
ssh -i "$SSH_KEY" "$SSH_USER@$VPS_IP" << 'REMOTE'
  set -e
  echo "Updating system..."
  apt update && apt upgrade -y

  echo "Installing Docker..."
  if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com | sh
    systemctl enable --now docker
  fi

  echo "Installing Docker Compose..."
  if ! command -v docker-compose &> /dev/null; then
    curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
  fi

  echo "Creating project directory..."
  mkdir -p /opt/aml-fraud-detector
  cd /opt/aml-fraud-detector
REMOTE

# 2. Upload the project files (or git clone)
echo "Uploading project files..."
rsync -avz --exclude 'node_modules' --exclude 'target' --exclude '.git' \
  -e "ssh -i $SSH_KEY" \
  . "$SSH_USER@$VPS_IP:$REPO_DIR/"

# 3. Run on remote
ssh -i "$SSH_KEY" "$SSH_USER@$VPS_IP" << 'REMOTE'
  cd /opt/aml-fraud-detector

  echo "Creating production .env..."
  cat > .env.prod << 'ENVEOF'
SPRING_PROFILES_ACTIVE=production
DB_HOST=postgres
DB_PORT=5432
DB_NAME=aml_fraud
DB_USER=aml_user
DB_PASSWORD=CHANGE_ME_STRONG_PASSWORD

AEROSPIKE_HOST=aerospike
AEROSPIKE_PORT=3000

KAFKA_BOOTSTRAP= kafka:9092

# Add your real secrets here
JWT_SECRET=CHANGE_ME_JWT_SECRET_32_CHARS_MIN
API_KEY=CHANGE_ME_API_KEY
ENVEOF

  echo "Starting production stack..."
  docker-compose -f docker-compose.prod.yml down || true
  docker-compose -f docker-compose.prod.yml up -d --build

  echo "Waiting for services..."
  sleep 30

  docker ps
  echo "Deployment complete. Backend should be on port 2637"
REMOTE

echo "=== Deployment finished ==="
echo "Access the app at http://$VPS_IP:2637/api/v1/swagger-ui.html"
echo "Frontend via nginx on port 80/443"