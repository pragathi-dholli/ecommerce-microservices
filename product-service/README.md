# Product Service

Microservice for product catalogue and inventory management — part of the E-Commerce Microservices Suite.

**Stack:** Java 17 · Spring Boot 3.2 · PostgreSQL · Resilience4j · OpenFeign · Docker · AWS ECS

---

## Quick Start (Local)

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker & Docker Compose

### Run with Docker Compose
```bash
# Clone and enter directory
cd product-service

# Copy environment file
cp .env.example .env

# Start PostgreSQL + Product Service
docker-compose up -d

# With pgAdmin UI (http://localhost:5050)
docker-compose --profile tools up -d
```

Service available at: `http://localhost:8081`

### Run Locally (without Docker)
```bash
# Start only PostgreSQL
docker-compose up -d postgres

# Run the app
./mvnw spring-boot:run
```

---

## Running Tests

```bash
# All tests
mvn verify

# Unit tests only
mvn test

# Integration tests only
mvn failsafe:integration-test
```

---

## API Reference

Base URL: `http://localhost:8081/api/v1/products`

| Method   | Endpoint                          | Description                          |
|----------|-----------------------------------|--------------------------------------|
| `POST`   | `/`                               | Create a new product                 |
| `GET`    | `/{id}`                           | Get product by ID                    |
| `GET`    | `/sku/{sku}`                      | Get product by SKU                   |
| `GET`    | `/`                               | List all products (paginated)        |
| `GET`    | `/search`                         | Search with filters                  |
| `GET`    | `/category/{category}`            | Filter by category                   |
| `GET`    | `/low-stock?threshold=10`         | Products below stock threshold       |
| `PATCH`  | `/{id}`                           | Partial update                       |
| `PATCH`  | `/{id}/deactivate`                | Deactivate product                   |
| `POST`   | `/inventory/check`                | Check stock availability (Feign)     |
| `POST`   | `/sku/{sku}/deduct-stock`         | Deduct stock (called by Order Svc)   |
| `POST`   | `/{id}/stock-adjustment`          | Manual stock adjustment              |
| `DELETE` | `/{id}`                           | Delete product (only if stock = 0)   |

### Example: Create Product
```bash
curl -X POST http://localhost:8081/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Wireless Headphones",
    "sku": "ELEC-WH-001",
    "price": 199.99,
    "stockQuantity": 100,
    "category": "Electronics",
    "brand": "SoundMax"
  }'
```

### Example: Search with Filters
```bash
curl "http://localhost:8081/api/v1/products/search?category=Electronics&minPrice=50&maxPrice=300&page=0&size=10"
```

### Example: Check Inventory (Order Service → Product Service)
```bash
curl -X POST http://localhost:8081/api/v1/products/inventory/check \
  -H "Content-Type: application/json" \
  -d '{ "sku": "ELEC-WH-001", "requestedQuantity": 5 }'
```

---

## Circuit Breaker

Resilience4j is configured for inter-service calls:

| Setting                  | Value  |
|--------------------------|--------|
| Sliding window size      | 10     |
| Failure rate threshold   | 50%    |
| Wait in open state       | 5s     |
| Slow call threshold      | 2s     |
| Retry attempts           | 3      |
| Retry backoff            | 500ms  |

View circuit breaker state:
```
GET http://localhost:8081/actuator/circuitbreakers
```

---

## Health & Monitoring

```
GET /actuator/health          # Service + DB health
GET /actuator/metrics         # All metrics
GET /actuator/circuitbreakers # Circuit breaker states
GET /actuator/prometheus      # Prometheus metrics scrape
```

---

## Project Structure

```
src/
├── main/java/com/ecommerce/product/
│   ├── ProductServiceApplication.java
│   ├── controller/       # REST endpoints
│   ├── service/          # Business logic
│   ├── repository/       # JPA queries
│   ├── model/            # JPA entities + enums
│   ├── dto/              # Request/Response DTOs
│   ├── exception/        # Custom exceptions + global handler
│   └── config/           # Feign client example
└── main/resources/
    ├── application.yml
    └── db/migration/
        ├── V1__create_products_table.sql
        └── V2__seed_sample_products.sql

test/
├── service/   ProductServiceTest.java         (unit)
├── controller/ProductControllerIntegrationTest.java
└── repository/ProductRepositoryTest.java
```

---

## AWS Deployment

### GitHub Secrets Required
```
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
```

### Deploy Steps
1. Push to `main` branch
2. GitHub Actions runs: **test → build → push to ECR → deploy to ECS**
3. ECS pulls new image and performs rolling deployment

### AWS Parameter Store (DB credentials)
```
/ecommerce/product/db-host
/ecommerce/product/db-port
/ecommerce/product/db-name
/ecommerce/product/db-user
/ecommerce/product/db-password
```

---

## Inter-Service Communication

The **Order Service** calls Product Service via OpenFeign:

```java
@FeignClient(name = "product-service", url = "${services.product.url}")
public interface ProductServiceClient {
    @PostMapping("/api/v1/products/inventory/check")
    InventoryCheckResponse checkInventory(@RequestBody InventoryCheckRequest req);

    @PostMapping("/api/v1/products/sku/{sku}/deduct-stock")
    void deductStock(@PathVariable String sku, @RequestParam int quantity);
}
```

See `config/ProductServiceClientExample.java` for full fallback implementation.

---

## Next Services
- `order-service` — creates orders, calls Product & Payment via Feign
- `payment-service` — processes payments, updates order status
