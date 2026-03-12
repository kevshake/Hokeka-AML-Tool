#!/bin/bash
# Complete dev environment startup for Fraud_Detector

set -e

echo "🚀 Starting Fraud_Detector Development Environment..."
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if running from correct directory
if [ ! -f "pom.xml" ]; then
    echo "❌ Error: Run this script from the BACKEND directory"
    echo "   cd /root/.openclaw/workspace/projects/fraud-detector/Fraud_Detector/BACKEND"
    exit 1
fi

# Start infrastructure
echo "📦 Starting infrastructure services..."
docker-compose up -d postgres

# Wait for PostgreSQL to be ready
echo "⏳ Waiting for PostgreSQL..."
until docker exec fraud-detector-postgres pg_isready -U fraud_detector -d fraud_detector > /dev/null 2>&1; do
    sleep 2
done
echo -e "${GREEN}✅ PostgreSQL is ready${NC}"
echo ""

# Build and run Spring Boot app
echo "🔨 Building Spring Boot application..."
mvn clean compile -q

echo "🚀 Starting Spring Boot application..."
echo -e "${YELLOW}Application will be available at:${NC}"
echo "  • API: http://localhost:2637/api/v1"
echo "  • Swagger UI: http://localhost:2637/api/v1/swagger-ui.html"
echo ""

# Run in background and capture PID
mvn spring-boot:run -Dspring-boot.run.profiles=dev -q &
APP_PID=$!

# Wait a moment for startup
sleep 5

# Check if process is still running
if kill -0 $APP_PID 2>/dev/null; then
    echo -e "${GREEN}✅ Backend is starting (PID: $APP_PID)${NC}"
    echo ""
    echo "📝 To view logs: tail -f ~/workspace/projects/fraud-detector/Fraud_Detector/BACKEND/target/*.log"
    echo "🛑 To stop: kill $APP_PID"
    echo ""
    echo "🎉 Development environment is ready!"
else
    echo "❌ Backend failed to start. Check logs."
    exit 1
fi

wait $APP_PID
