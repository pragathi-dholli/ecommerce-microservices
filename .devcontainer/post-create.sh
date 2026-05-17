#!/bin/bash
set -e

echo "================================================"
echo "  E-Commerce Microservices — Codespace Setup"
echo "================================================"

# ── Java 17 ─────────────────────────────────────────────
echo "→ Installing Java 17 directly..."
sudo apt-get update -q
sudo apt-get install -y openjdk-17-jdk

sudo update-alternatives --set java /usr/lib/jvm/java-17-openjdk-amd64/bin/java
sudo update-alternatives --set javac /usr/lib/jvm/java-17-openjdk-amd64/bin/javac

echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

java -version
echo "✅ Done"

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