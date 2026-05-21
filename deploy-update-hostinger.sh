#!/bin/bash
# Update deployment on Hostinger VPS (pull latest + rebuild)

set -e

VPS_IP=${1:-"YOUR_VPS_IP_HERE"}
SSH_USER=${2:-"root"}
SSH_KEY=${3:-"~/.ssh/id_rsa"}

echo "=== Updating Hokeka AML on Hostinger VPS: $VPS_IP ==="

ssh -i "$SSH_KEY" "$SSH_USER@$VPS_IP" << 'REMOTE_SCRIPT'
  set -e
  cd /opt/aml-fraud-detector || { echo "Project dir not found"; exit 1; }

  echo "Pulling latest code from GitHub..."
  git fetch origin
  git checkout main
  git pull origin main

  echo "Rebuilding and restarting containers..."
  docker-compose -f docker-compose.prod.yml down
  docker-compose -f docker-compose.prod.yml up -d --build

  echo "Waiting for services to start..."
  sleep 20

  echo "Current running containers:"
  docker ps

  echo "Deployment update complete."
REMOTE_SCRIPT

echo "=== Update finished on Hostinger ==="