#!/bin/bash
set -e

echo "================================================"
echo "  E-Commerce Microservices — Codespace Setup"
echo "================================================"

# ── Force Java 17 ─────────────────────────────────────────────
echo "→ Forcing Java 17..."
source /root/.sdkman/bin/sdkman-init.sh
sdk install java 17.0.10-tem
sdk default java 17.0.10-tem
echo 'export JAVA_HOME=/root/.sdkman/candidates/java/current' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
java -version
echo "✅ Java 17 active"

# ── Install Maven if not present ──────────────────────────────
if ! command -v mvn &> /dev/null; then
  echo "→ Installing Maven..."
  sudo apt-get update -q && sudo apt-get install -y -q maven
fi
echo "→ Maven: $(mvn -version 2>&1 | head -1)"

# ── Pre-fetch Maven dependencies ──────────────────────────────
echo "→ Pre-fetching Maven dependencies..."
for svc in product-service order-service payment-service; do
  echo "  Fetching $svc..."
  (cd "$svc" && mvn dependency:go-offline -q 2>/dev/null || true)
done

echo ""
echo "================================================"
echo "  Setup complete! Quick-start commands:"
echo "    make start      → start all services"
echo "    make test-all   → run all 113 tests"
echo "    make health     → check service health"
echo "    make logs       → tail all logs"
echo ""
echo "  Service URLs:"
echo "    Product → http://localhost:8081"
echo "    Order   → http://localhost:8082"
echo "    Payment → http://localhost:8083"
echo "================================================"