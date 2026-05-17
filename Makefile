.PHONY: all build-all test-all start stop restart logs clean help \
        build-product build-order build-payment \
        test-product test-order test-payment \
        start-product start-order start-payment

# ── Default ────────────────────────────────────────────────────────────────
all: help

# ── BUILD ──────────────────────────────────────────────────────────────────
build-all: build-product build-order build-payment
	@echo "✅ All services built"

build-product:
	@echo "→ Building product-service..."
	cd product-service && mvn clean package -DskipTests -q
	@echo "✅ product-service built"

build-order:
	@echo "→ Building order-service..."
	cd order-service && mvn clean package -DskipTests -q
	@echo "✅ order-service built"

build-payment:
	@echo "→ Building payment-service..."
	cd payment-service && mvn clean package -DskipTests -q
	@echo "✅ payment-service built"

# ── TEST ───────────────────────────────────────────────────────────────────
test-all: test-product test-order test-payment
	@echo "✅ All tests passed"

test-product:
	@echo "→ Testing product-service..."
	cd product-service && mvn verify -q
	@echo "✅ product-service tests passed"

test-order:
	@echo "→ Testing order-service..."
	cd order-service && mvn verify -q
	@echo "✅ order-service tests passed"

test-payment:
	@echo "→ Testing payment-service..."
	cd payment-service && mvn verify -q
	@echo "✅ payment-service tests passed"

# ── DOCKER ────────────────────────────────────────────────────────────────
start:
	@echo "→ Starting full suite..."
	docker-compose up -d
	@echo "✅ All services started"
	@echo "   Product Service → http://localhost:8081"
	@echo "   Order Service   → http://localhost:8082"
	@echo "   Payment Service → http://localhost:8083"

start-tools:
	@echo "→ Starting suite + pgAdmin..."
	docker-compose --profile tools up -d
	@echo "✅ pgAdmin available at http://localhost:5050"

stop:
	docker-compose down
	@echo "✅ All services stopped"

restart:
	docker-compose down
	docker-compose up -d
	@echo "✅ All services restarted"

logs:
	docker-compose logs -f

logs-product:
	docker-compose logs -f product-service

logs-order:
	docker-compose logs -f order-service

logs-payment:
	docker-compose logs -f payment-service

# ── INDIVIDUAL SERVICE START (dev mode) ───────────────────────────────────
start-product:
	docker-compose up -d product-db
	cd product-service && mvn spring-boot:run

start-order:
	docker-compose up -d order-db
	cd order-service && mvn spring-boot:run

start-payment:
	docker-compose up -d payment-db
	cd payment-service && mvn spring-boot:run

# ── HEALTH CHECKS ─────────────────────────────────────────────────────────
health:
	@echo "→ Checking service health..."
	@curl -sf http://localhost:8081/actuator/health | python3 -c "import sys,json; d=json.load(sys.stdin); print('  Product Service:', d['status'])" 2>/dev/null || echo "  Product Service: OFFLINE"
	@curl -sf http://localhost:8082/actuator/health | python3 -c "import sys,json; d=json.load(sys.stdin); print('  Order Service:  ', d['status'])" 2>/dev/null || echo "  Order Service:   OFFLINE"
	@curl -sf http://localhost:8083/actuator/health | python3 -c "import sys,json; d=json.load(sys.stdin); print('  Payment Service:', d['status'])" 2>/dev/null || echo "  Payment Service: OFFLINE"

# ── CLEANUP ───────────────────────────────────────────────────────────────
clean:
	cd product-service && mvn clean -q
	cd order-service   && mvn clean -q
	cd payment-service && mvn clean -q
	@echo "✅ Build artifacts cleaned"

clean-all: clean
	docker-compose down -v --remove-orphans
	@echo "✅ Containers and volumes removed"

# ── HELP ──────────────────────────────────────────────────────────────────
help:
	@echo ""
	@echo "E-Commerce Microservices Suite"
	@echo "══════════════════════════════"
	@echo ""
	@echo "  Build:"
	@echo "    make build-all       Build all 3 services (skip tests)"
	@echo "    make build-product   Build product-service only"
	@echo "    make build-order     Build order-service only"
	@echo "    make build-payment   Build payment-service only"
	@echo ""
	@echo "  Test:"
	@echo "    make test-all        Run all tests across all services"
	@echo "    make test-product    Test product-service only"
	@echo "    make test-order      Test order-service only"
	@echo "    make test-payment    Test payment-service only"
	@echo ""
	@echo "  Run:"
	@echo "    make start           Start full suite via Docker Compose"
	@echo "    make start-tools     Start suite + pgAdmin UI"
	@echo "    make stop            Stop all containers"
	@echo "    make restart         Restart all containers"
	@echo "    make health          Check health of all services"
	@echo ""
	@echo "  Logs:"
	@echo "    make logs            Tail logs from all services"
	@echo "    make logs-product    Tail product-service logs"
	@echo "    make logs-order      Tail order-service logs"
	@echo "    make logs-payment    Tail payment-service logs"
	@echo ""
	@echo "  Cleanup:"
	@echo "    make clean           Remove Maven build artifacts"
	@echo "    make clean-all       Remove artifacts + Docker volumes"
	@echo ""
	@echo "  Service URLs:"
	@echo "    Product Service → http://localhost:8081/actuator/health"
	@echo "    Order Service   → http://localhost:8082/actuator/health"
	@echo "    Payment Service → http://localhost:8083/actuator/health"
	@echo ""
