@echo off
setlocal
cd /d "%~dp0"

echo Starting AML backend (profile: local = dev + in-memory H2, no Docker/Postgres)...
echo API: http://localhost:2637/api/v1
echo Login: super.admin / Hokeka2026!
echo.

call mvn package -Dmaven.test.skip=true -q
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

java -jar target\aml-fraud-detector-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
