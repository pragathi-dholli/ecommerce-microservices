# Order Service

Orchestrates the full order lifecycle — validates inventory, reserves stock, initiates payment, and manages order status transitions. Calls **Product Service** and **Payment Service** via OpenFeign with Resilience4j circuit breakers.

**Stack:** Java 17 · Spring Boot 3.2 · PostgreSQL · OpenFeign · Resilience4j · Docker · AWS ECS

---

## Quick Start (Local)

### Prerequisites
- Java 17+, Maven 3.9+, Docker & Docker Compose
- Product Service running on port 8081
- Payment Service running on port 8083

### Run with Docker Compose
```bash
cd order-service
cp .env.example .env

# Create the shared network first (if not already created)
docker network create ecommerce-network

docker-compose up -d

# Optional: include pgAdmin on http://localhost:5051
docker-compose --profile tools up -d
```

Service available at: `http://localhost:8082`

### Run Locally (without Docker)
```bash
# Start only the DB
docker-compose up -d order-db

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

Tests use H2 in-memory DB and mock all Feign clients — no real downstream services needed.

---

## API Reference

Base URL: `http://localhost:8082/api/v1/orders`

| Method  | Endpoint                        | Description                                      |
|---------|---------------------------------|--------------------------------------------------|
| `POST`  | `/`                             | Create order (validates stock + initiates payment) |
| `GET`   | `/{id}`                         | Get order by ID                                  |
| `GET`   | `/number/{orderNumber}`         | Get order by order number (with items)           |
| `GET`   | `/`                             | List all orders (filterable, paginated)          |
| `GET`   | `/customer/{customerId}`        | List orders for a customer                       |
| `PATCH` | `/{id}/status`                  | Update order status                              |
| `POST`  | `/{id}/cancel`                  | Cancel order (restores stock + triggers refund)  |
| `POST`  | `/payment-callback`             | Receive payment result from Payment Service      |

### Example: Create Order
```bash
curl -X POST http://localhost:8082/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "order": {
      "customerId": "CUST-001",
      "customerEmail": "jane@example.com",
      "items": [
        { "productSku": "ELEC-WH-001", "quantity": 2 }
      ],
      "shippingAddress": {
        "fullName": "Jane Doe",
        "addressLine1": "123 Main St",
        "city": "Austin",
        "state": "TX",
        "postalCode": "78701",
        "country": "US"
      }
    },
    "paymentMethod": {
      "type": "CARD",
      "token": "tok_visa_4242"
    }
  }'
```

### Example: Update Status
```bash
curl -X PATCH http://localhost:8082/api/v1/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{ "status": "SHIPPED", "notes": "Tracking: 1Z999AA10123456784" }'
```

### Example: Payment Callback (from Payment Service)
```bash
curl -X POST http://localhost:8082/api/v1/orders/payment-callback \
  -H "Content-Type: application/json" \
  -d '{
    "orderNumber": "ORD-A1B2C3D4",
    "paymentId": "PAY-XYZ789",
    "paymentStatus": "COMPLETED"
  }'
```

### Example: Filter Orders
```bash
curl "http://localhost:8082/api/v1/orders?customerId=CUST-001&status=CONFIRMED&page=0&size=10"
```

---

## Order Lifecycle

```
PENDING ──► CONFIRMED ──► PROCESSING ──► SHIPPED ──► DELIVERED
   │              │               │
   └──────────────┴───────────────┴──► CANCELLED ──► REFUNDED
```

| Transition              | Trigger                              |
|-------------------------|--------------------------------------|
| PENDING → CONFIRMED     | Payment callback: COMPLETED          |
| PENDING → CANCELLED     | Payment callback: FAILED / timeout   |
| CONFIRMED → PROCESSING  | Manual status update (admin/ops)     |
| PROCESSING → SHIPPED    | Manual status update                 |
| SHIPPED → DELIVERED     | Manual status update / webhook       |
| ANY → CANCELLED         | Customer cancel request              |
| DELIVERED → REFUNDED    | Refund request                       |

---

## Order Creation Flow

```
Client
  │
  ▼
Order Service
  ├─ 1. Check inventory   ──► Product Service  (circuit breaker)
  ├─ 2. Save order as PENDING
  ├─ 3. Deduct stock       ──► Product Service
  │       └─ FAIL: restore stock → cancel order
  ├─ 4. Initiate payment   ──► Payment Service  (circuit breaker)
  │       └─ FAIL: restore stock → cancel order
  └─ 5. Update status based on payment response
            COMPLETED → CONFIRMED
            PENDING   → stays PENDING (awaits callback)
            FAILED    → CANCELLED + restore stock
```

---

## Circuit Breaker Configuration

Two independent circuit breakers — one per downstream service:

| Setting                    | Product Service | Payment Service |
|----------------------------|-----------------|-----------------|
| Sliding window             | 10 calls        | 10 calls        |
| Failure rate threshold     | 50%             | 40% (stricter)  |
| Open state wait            | 10s             | 15s             |
| Timeout per call           | 4s              | 10s             |
| Retry attempts             | 3               | 2 (avoid double charge) |

View live circuit breaker states:
```
GET http://localhost:8082/actuator/circuitbreakers
```

---

## Stale Order Cleanup

A scheduled job runs every 5 minutes and auto-cancels orders stuck in `PENDING` for more than 30 minutes (configurable). For each cancelled order, it:
1. Restores stock in Product Service
2. Logs any restore failures for manual reconciliation

Configure via `application.yml`:
```yaml
order:
  pending-timeout-minutes: 30
  timeout-check-interval-ms: 300000
```

---

## Project Structure

```
src/
├── main/java/com/ecommerce/order/
│   ├── OrderServiceApplication.java
│   ├── controller/     OrderController.java
│   ├── service/        OrderService.java
│   │                   OrderTimeoutScheduler.java
│   ├── repository/     OrderRepository.java
│   ├── model/          Order.java, OrderItem.java
│   │                   ShippingAddress.java
│   │                   OrderStatus.java, PaymentStatus.java
│   ├── dto/            OrderDto.java, ExternalServiceDto.java
│   ├── client/         ProductServiceClient.java
│   │                   PaymentServiceClient.java
│   └── exception/      (5 exception classes)
└── main/resources/
    ├── application.yml
    └── db/migration/
        └── V1__create_orders_tables.sql

test/
├── service/    OrderServiceTest.java          (unit — 15 tests)
├── controller/ OrderControllerIntegrationTest.java (16 tests)
└── repository/ OrderRepositoryTest.java       (9 tests)
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
/ecommerce/order/db-host
/ecommerce/order/db-port
/ecommerce/order/db-name
/ecommerce/order/db-user
/ecommerce/order/db-password
/ecommerce/order/product-service-url
/ecommerce/order/payment-service-url
```

### Deploy Pipeline
Push to `main` → GitHub Actions runs **test → build → ECR push → ECS deploy**

---

## Health & Monitoring

```
GET /actuator/health           # Service + DB health
GET /actuator/circuitbreakers  # Circuit breaker states
GET /actuator/metrics          # All metrics
GET /actuator/prometheus       # Prometheus scrape endpoint
```

---

## Running the Full Suite Locally

To run all three services together:

```bash
# 1. Create shared network
docker network create ecommerce-network

# 2. Start each service
cd product-service && docker-compose up -d && cd ..
cd payment-service && docker-compose up -d && cd ..
cd order-service   && docker-compose up -d && cd ..
```

| Service         | Port  |
|-----------------|-------|
| Product Service | 8081  |
| Order Service   | 8082  |
| Payment Service | 8083  |
