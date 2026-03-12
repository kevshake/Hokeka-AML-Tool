#!/bin/bash
# Complete Fraud_Detector development environment launcher
# Run from: /root/.openclaw/workspace/projects/fraud-detector/Fraud_Detector/

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     🏦 Fraud_Detector Development Environment         ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════╝${NC}"
echo ""

BASE_DIR="/root/.openclaw/workspace/projects/fraud-detector/Fraud_Detector"

echo -e "${YELLOW}📦 Starting infrastructure...${NC}"
cd "$BASE_DIR/BACKEND"
docker-compose up -d postgres

echo "⏳ Waiting for PostgreSQL..."
until docker exec fraud-detector-postgres pg_isready -U fraud_detector -d fraud_detector > /dev/null 2>&1; do
    sleep 1
done
echo -e "${GREEN}✅ PostgreSQL ready${NC}"
echo ""

echo -e "${YELLOW}🔨 Building backend...${NC}"
mvn clean compile -q

echo -e "${YELLOW}🚀 Starting backend (Spring Boot)...${NC}"
mvn spring-boot:run -Dspring-boot.run.profiles=dev > /tmp/backend.log 2>&1 &
echo $! > /tmp/backend.pid
sleep 10

if kill -0 $(cat /tmp/backend.pid) 2>/dev/null; then
    echo -e "${GREEN}✅ Backend running on http://localhost:2637${NC}"
else
    echo -e "${YELLOW}⚠️  Backend starting... check /tmp/backend.log${NC}"
fi
echo ""

echo -e "${YELLOW}📦 Installing frontend dependencies...${NC}"
cd "$BASE_DIR/FRONTEND"
if [ ! -d "node_modules" ]; then
    npm install
fi

echo -e "${YELLOW}🚀 Starting frontend (Vite)...${NC}"
npm run dev > /tmp/frontend.log 2>&1 &
echo $! > /tmp/frontend.pid
sleep 5

echo ""
echo -e "${GREEN}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  🎉 Development Environment Ready!                     ║${NC}"
echo -e "${GREEN}╠════════════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║  Backend:  http://localhost:2637/api/v1               ║${NC}"
echo -e "${GREEN}║  Frontend: http://localhost:5173                      ║${NC}"
echo -e "${GREEN}║  Swagger:  http://localhost:2637/api/v1/swagger-ui    ║${NC}"
echo -e "${GREEN}╠════════════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║  Logs:    /tmp/backend.log | /tmp/frontend.log        ║${NC}"
echo -e "${GREEN}║  Stop:    ./stop-dev.sh                               ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════╝${NC}"
echo ""

# Wait for interrupt
trap 'echo ""; echo "🛑 Stopping services..."; kill $(cat /tmp/backend.pid /tmp/frontend.pid) 2>/dev/null; docker-compose down; exit 0' INT
wait
