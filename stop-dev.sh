#!/bin/bash
# Stop Fraud_Detector development environment

echo "🛑 Stopping Fraud_Detector services..."

# Stop backend
if [ -f /tmp/backend.pid ]; then
    kill $(cat /tmp/backend.pid) 2>/dev/null && echo "✅ Backend stopped"
    rm -f /tmp/backend.pid
fi

# Stop frontend
if [ -f /tmp/frontend.pid ]; then
    kill $(cat /tmp/frontend.pid) 2>/dev/null && echo "✅ Frontend stopped"
    rm -f /tmp/frontend.pid
fi

# Stop Docker services
cd /root/.openclaw/workspace/projects/fraud-detector/Fraud_Detector/BACKEND
docker-compose down && echo "✅ Infrastructure stopped"

echo ""
echo "👋 Development environment stopped."
