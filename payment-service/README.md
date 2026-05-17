# Payment Service

Processes payments and refunds for the E-Commerce Microservices Suite. Receives payment requests from **Order Service**, charges the payment gateway, and sends results back via callback. Includes idempotency guards, partial refunds, and a scheduled reconciliation job for stuck payments.

**Stack:** Java 17 · Spring Boot 3.2 · PostgreSQL · OpenFeign · Resilience4j · Docker · AWS ECS

---

## Quick Start (Local)

### Prerequisites
- Java 17+, Maven 3.9+, Docker & Docker Compose

### Run with Docker Compose
```bash
cd payment-service
cp .env.example .env

# Create shared network if not already created
docker network create ecommerce-network

docker-compose up -d

# Optional: include pgAdmin on http://localhost:5052
docker-compose --profile tools up -d
```

Service available at: `http://localhost:8083`

### Run Locally (without Docker)
```bash
docker-compose up -d payment-db
./mvnw spring-boot:run
```

---

## Running Tests

```bash
# All tests (unit + integration)
mvn verify

# Unit tests only
mvn test

# Integration tests only
mvn failsafe:integration-test
```

Tests use H2 in-memory DB. The payment gateway and Order Service client are mocked via `@MockBean` — no external services needed.

---

## API Reference

Base URL: `http://localhost:8083/api/v1/payments`

| Method | Endpoint                              | Description                                        |
|--------|---------------------------------------|----------------------------------------------------|
| `POST` | `/`                                   | Initiate payment (called by Order Service)         |
| `GET`  | `/{paymentId}`                        | Get payment by internal payment ID                 |
| `GET`  | `/order/{orderNumber}`                | Get payment by order number                        |
| `GET`  | `/`                                   | List all payments (filterable, paginated)          |
| `GET`  | `/customer/{customerId}`              | List payments for a customer                       |
| `POST` | `/{paymentId}/refund`                 | Issue full or partial refund                       |
| `POST` | `/order/{orderNumber}/refund`         | Refund by order number (used by Order cancel flow) |

### Example: Initiate Payment
```bash
curl -X POST http://localhost:8083/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "orderNumber": "ORD-A1B2C3D4",
    "amount": 219.97,
    "currency": "USD",
    "customerId": "CUST-001",
    "customerEmail": "jane@example.com",
    "paymentMethod": {
      "type": "CARD",
      "token": "tok_visa_4242"
    }
  }'
```

### Example: Full Refund
```bash
curl -X POST http://localhost:8083/api/v1/payments/PAY-ABC12345/refund \
  -H "Content-Type: application/json" \
  -d '{ "reason": "Customer cancelled order" }'
```

### Example: Partial Refund
```bash
curl -X POST http://localhost:8083/api/v1/payments/PAY-ABC12345/refund \
  -H "Content-Type: application/json" \
  -d '{ "amount": 50.00, "reason": "Partial item return" }'
```

### Example: Filter Payments
```bash
curl "http://localhost:8083/api/v1/payments?status=COMPLETED&customerId=CUST-001&page=0&size=10"
```

---

## Payment Flow

```
Order Service
    │
    │  POST /api/v1/payments
    ▼
Payment Service
    ├─ 1. Idempotency check (reject duplicate for same order)
    ├─ 2. Save as PENDING
    ├─ 3. Mark PROCESSING → call gateway
    │       COMPLETED → save gatewayReference
    │       FAILED    → save failureReason
    └─ 4. Notify Order Service via callback
              POST /api/v1/orders/payment-callback
```

---

## Payment Status Lifecycle

```
PENDING ──► PROCESSING ──► COMPLETED ──► REFUNDED
                │                   └──► PARTIALLY_REFUNDED
                └──► FAILED
```

---

## Gateway Simulation

The `PaymentGatewayClient` simulates a real payment gateway. In production, replace it with your gateway SDK (Stripe, Adyen, etc.) — the rest of the service is unchanged.

**Simulation behaviour based on token prefix:**

| Token prefix        | Result              | Decline code         |
|---------------------|---------------------|----------------------|
| `tok_success_*`     | COMPLETED           | —                    |
| `tok_decline_funds` | FAILED              | `insufficient_funds` |
| `tok_decline_expired` | FAILED            | `card_expired`       |
| `tok_decline_cvc`   | FAILED              | `incorrect_cvc`      |
| `tok_error_*`       | FAILED              | `gateway_error`      |
| anything else       | COMPLETED (default) | —                    |

---

## Idempotency

Each order number maps to exactly one payment record. Submitting a second payment request for the same `orderNumber` returns **409 Conflict**. This prevents double-charging if Order Service retries due to a network timeout.

---

## Reconciliation Job

A scheduled job runs every 5 minutes and auto-fails payments stuck in `PROCESSING` for more than 10 minutes (configurable). For each reconciled payment it:
1. Sets status to `FAILED`
2. Notifies Order Service via callback so the order isn't left blocked

Configure via `application.yml`:
```yaml
payment:
  stale-processing-minutes: 10
  reconciliation-interval-ms: 300000
```

---

## Circuit Breaker — Order Service Callback

The callback to Order Service is protected by Resilience4j:

| Setting                | Value |
|------------------------|-------|
| Sliding window         | 10    |
| Failure rate threshold | 50%   |
| Open state wait        | 10s   |
| Retry attempts         | 3     |
| Call timeout           | 5s    |

If the circuit is open, the fallback logs the event for manual reconciliation — the payment record is still saved correctly.

---

## Project Structure

```
src/
├── main/java/com/ecommerce/payment/
│   ├── PaymentServiceApplication.java
│   ├── controller/   PaymentController.java
│   ├── service/      PaymentService.java
│   │                 PaymentReconciliationScheduler.java
│   ├── repository/   PaymentRepository.java
│   ├── model/        Payment.java
│   │                 PaymentStatus.java, PaymentMethod.java
│   ├── dto/          PaymentDto.java
│   ├── client/       OrderServiceClient.java
│   ├── config/       PaymentGatewayClient.java
│   └── exception/    (4 exception classes)
└── main/resources/
    ├── application.yml
    └── db/migration/
        └── V1__create_payments_table.sql

test/
├── service/    PaymentServiceTest.java            (unit — 13 tests)
├── controller/ PaymentControllerIntegrationTest.java (14 tests)
└── repository/ PaymentRepositoryTest.java         (9 tests)
```

---

## AWS Deployment

### GitHub Secrets Required
```
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
```

### AWS Parameter Store (secrets injected at runtime)
```
/ecommerce/payment/db-host
/ecommerce/payment/db-port
/ecommerce/payment/db-name
/ecommerce/payment/db-user
/ecommerce/payment/db-password
/ecommerce/payment/order-service-url
```

### Deploy Pipeline
Push to `main` → GitHub Actions runs **test → build → ECR push → ECS deploy**

---

## Health & Monitoring

```
GET /actuator/health           # Service + DB health
GET /actuator/circuitbreakers  # Circuit breaker state (orderService)
GET /actuator/metrics          # All metrics
GET /actuator/prometheus       # Prometheus scrape endpoint
```

---

## Running the Full Suite Locally

```bash
# 1. Shared network (once)
docker network create ecommerce-network

# 2. Start all three services
cd product-service && docker-compose up -d && cd ..
cd order-service   && docker-compose up -d && cd ..
cd payment-service && docker-compose up -d && cd ..
```

| Service         | Port | DB Port |
|-----------------|------|---------|
| Product Service | 8081 | 5432    |
| Order Service   | 8082 | 5433    |
| Payment Service | 8083 | 5434    |
