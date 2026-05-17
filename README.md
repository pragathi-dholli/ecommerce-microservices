# E-Commerce Microservices Suite

A production-grade microservices application built with Java 17, Spring Boot 3.2, PostgreSQL, Resilience4j, and Docker. Deployable to AWS ECS via GitHub Actions CI/CD.

[![CI](https://github.com/pragathi-dholli/ecommerce-microservices/actions/workflows/ci.yml/badge.svg)](https://github.com/pragathi-dholli/ecommerce-microservices/actions)

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        Client                           │
└──────────┬──────────────────┬───────────────────────────┘
           │                  │
           ▼                  ▼
┌──────────────────┐ ┌─────────────────┐
│ Product Service  │ │  Order Service  │
│   Port: 8081     │ │   Port: 8082    │
│                  │ │                 │
│ • Product CRUD   │ │ • Create orders │
│ • Inventory mgmt │ │ • Orchestrates  │
│ • Stock reserve  │ │   Product +     │
│                  │ │   Payment calls │
└──────┬───────────┘ └────┬────────────┘
       │   ▲              │  ▲
       │   │ Feign+CB     │  │ Feign+CB
       │   └──────────────┘  │
       │                     ▼
       │            ┌──────────────────┐
       │            │ Payment Service  │
       │            │   Port: 8083     │
       │            │                  │
       │            │ • Process payments│
       │            │ • Refunds        │
       │            │ • Reconciliation │
       │            └──────────────────┘
       │
┌──────▼──────────────────────────────────┐
│         PostgreSQL (3 separate DBs)      │
│  product_db  |  order_db  | payment_db  │
└─────────────────────────────────────────┘
```

---

## Services

| Service | Port | DB Port | Description |
|---------|------|---------|-------------|
| [Product Service](./product-service/README.md) | 8081 | 5432 | Product catalogue, inventory, stock management |
| [Order Service](./order-service/README.md)     | 8082 | 5433 | Order lifecycle, orchestrates Product + Payment |
| [Payment Service](./payment-service/README.md) | 8083 | 5434 | Payment processing, refunds, reconciliation |

---

## Quick Start

### Option A — GitHub Codespaces (recommended)

1. Click **Code → Codespaces → Create codespace on main**
2. Wait for the container to build (~2 min)
3. Run:
   ```bash
   make start
   ```
4. Codespaces will auto-forward ports 8081, 8082, 8083

### Option B — Local with Docker

**Prerequisites:** Docker Desktop, Java 17, Maven 3.9

```bash
git clone https://github.com/pragathi-dholli/ecommerce-microservices.git
cd ecommerce-microservices

# Start everything
make start

# Or with pgAdmin UI (http://localhost:5050)
make start-tools
```

### Option C — Run services individually (for development)
```bash
# Terminal 1 — databases + product service
make start-product

# Terminal 2
make start-payment

# Terminal 3
make start-order
```

---

## Make Commands

```bash
make help          # Show all available commands

# Build
make build-all     # Build all 3 services
make build-product # Build product-service only

# Test
make test-all      # Run all 113 tests across all services
make test-product  # Test product-service only

# Run
make start         # Start full suite (Docker Compose)
make start-tools   # Start suite + pgAdmin
make stop          # Stop all services
make restart       # Restart all services
make health        # Check health of all running services
make logs          # Tail logs from all services

# Cleanup
make clean         # Remove Maven build artifacts
make clean-all     # Remove artifacts + Docker volumes
```

---

## Tech Stack

| Concern | Technology |
|---------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Inter-service calls | OpenFeign |
| Fault tolerance | Resilience4j (Circuit Breaker, Retry, TimeLimiter) |
| Database | PostgreSQL 16 |
| DB migrations | Flyway |
| ORM | Spring Data JPA / Hibernate |
| Containerisation | Docker (multi-stage builds) |
| Orchestration | AWS ECS Fargate |
| CI/CD | GitHub Actions |
| Secrets | AWS Systems Manager Parameter Store |
| Testing | JUnit 5, Mockito, MockMvc, H2 |

---

## Order Creation Flow

```
Client → POST /api/v1/orders
           │
           ├─ 1. Check stock availability   → Product Service (circuit breaker)
           ├─ 2. Save order as PENDING
           ├─ 3. Deduct stock               → Product Service
           │         └─ FAIL: restore stock + cancel order
           ├─ 4. Initiate payment           → Payment Service (circuit breaker)
           │         └─ FAIL: restore stock + cancel order
           └─ 5. Update order status
                     COMPLETED → CONFIRMED
                     PENDING   → wait for callback
                     FAILED    → CANCELLED + restore stock
```

---

## Circuit Breaker Configuration

Each service-to-service call is protected by Resilience4j:

| Caller | Callee | Failure Threshold | Open Wait | Retries |
|--------|--------|-------------------|-----------|---------|
| Order | Product | 50% | 10s | 3 |
| Order | Payment | 40% | 15s | 2 |
| Payment | Order (callback) | 50% | 10s | 3 |

---

## API Quick Reference

### Product Service (`http://localhost:8081`)
```
POST   /api/v1/products                    Create product
GET    /api/v1/products/{id}               Get by ID
GET    /api/v1/products/sku/{sku}          Get by SKU
GET    /api/v1/products/search             Search with filters
POST   /api/v1/products/inventory/check    Check availability (Feign)
POST   /api/v1/products/sku/{sku}/deduct-stock  Deduct stock (Feign)
```

### Order Service (`http://localhost:8082`)
```
POST   /api/v1/orders                      Create order (full flow)
GET    /api/v1/orders/{id}                 Get by ID
GET    /api/v1/orders/number/{orderNumber} Get by order number
PATCH  /api/v1/orders/{id}/status          Update status
POST   /api/v1/orders/{id}/cancel          Cancel order
POST   /api/v1/orders/payment-callback     Payment result callback
```

### Payment Service (`http://localhost:8083`)
```
POST   /api/v1/payments                    Initiate payment
GET    /api/v1/payments/{paymentId}        Get by payment ID
GET    /api/v1/payments/order/{orderNumber} Get by order number
POST   /api/v1/payments/{paymentId}/refund Full or partial refund
POST   /api/v1/payments/order/{orderNumber}/refund  Refund by order
```

See `api-requests.http` for ready-to-run VS Code REST Client requests.

---

## Testing

```bash
make test-all
```

| Service | Unit Tests | Integration Tests | Repository Tests | Total |
|---------|-----------|------------------|-----------------|-------|
| Product | 12 | 16 | 9 | 37 |
| Order   | 15 | 16 | 9 | 40 |
| Payment | 13 | 14 | 9 | 36 |
| **Total** | **40** | **46** | **27** | **113** |

Tests use H2 in-memory DB and mock all Feign clients — no external services needed.

---

## AWS Deployment

### Prerequisites
1. AWS account with ECS cluster named `ecommerce-cluster`
2. Three ECR repositories:
   - `ecommerce/product-service`
   - `ecommerce/order-service`
   - `ecommerce/payment-service`
3. GitHub secrets: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`

### Parameter Store secrets (per service)
```
/ecommerce/product/db-*
/ecommerce/order/db-*   + product-service-url + payment-service-url
/ecommerce/payment/db-* + order-service-url
```

### Deploy
Push to `main` → GitHub Actions automatically:
1. Runs all tests
2. Builds Docker image
3. Pushes to ECR
4. Updates ECS task definition
5. Deploys with rolling update

---

## Project Structure

```
ecommerce-microservices/
├── .devcontainer/
│   ├── devcontainer.json     # Codespaces config (Java 17 + Docker)
│   └── post-create.sh        # Auto-setup on Codespace create
├── .github/
│   └── workflows/
│       └── ci.yml            # Full suite CI (all 3 services)
├── product-service/          # Port 8081 — 33 files
├── order-service/            # Port 8082 — 33 files
├── payment-service/          # Port 8083 — 29 files
├── scripts/
│   └── pgadmin-servers.json  # pgAdmin pre-configured connections
├── api-requests.http         # VS Code REST Client requests
├── docker-compose.yml        # Full suite in one command
├── Makefile                  # Convenient dev commands
└── README.md
```
