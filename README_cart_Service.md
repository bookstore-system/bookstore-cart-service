# bookstore-cart-service

Microservice quản lý giỏ hàng cho hệ thống Bookstore. Lưu trữ giỏ hàng tạm thời trên **Redis** (TTL 7 ngày), gọi `book-service` qua **OpenFeign** để xác minh giá / tồn kho.

## 1. Tech Stack

| Hạng mục | Công nghệ |
| --- | --- |
| Ngôn ngữ | Java 21 |
| Framework | Spring Boot 4.0.5, Spring Cloud 2025.1.1 |
| Datastore | Redis 7 (key `cart:{userId}`, value JSON, TTL 7 ngày) |
| Inter-service | Spring Cloud OpenFeign → `book-service` |
| Validation | `spring-boot-starter-validation` |
| Build | Maven Wrapper (`./mvnw`) |
| Container | Docker + Docker Compose |

## 2. Cổng & quy ước

| Thành phần | Host port | Container port |
| --- | --- | --- |
| `cart-service` | **8083** | 8080 |
| `cart-redis`   | **6379** | 6379 |
| `api-gateway`  | 8080     | 8080 |

> Gateway xác thực JWT và forward header `X-User-Id` (**UUID**) và `X-User-Role` tới service. Khi test trực tiếp vào `:8083`, bạn tự gắn 2 header này.

### Swagger UI (SpringDoc OpenAPI 3)

- **UI:** `http://localhost:8083/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8083/v3/api-docs`
- Trên Swagger UI dùng **Authorize** để nhập `X-User-Id` và `X-User-Role`.

Production có thể tắt: `springdoc.api-docs.enabled=false` và `springdoc.swagger-ui.enabled=false`.

## 3. Biến môi trường

| Biến | Mặc định (local) | Mặc định (docker) | Mô tả |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | `docker` | profile cấu hình (`application-dev.yaml` / `application-docker.yaml`) |
| `REDIS_HOST` | `localhost` | `cart-redis` | hostname Redis |
| `REDIS_PORT` | `6379` | `6379` | cổng Redis |
| `BOOK_SERVICE_URL` | `http://localhost:8082` | `http://book-service:8080` | URL book-service |
| `SPRING_RABBITMQ_HOST` | `localhost` | `rabbitmq` | RabbitMQ (saga checkout) |
| `CART_TTL_DAYS` | `7` | `7` | TTL key `cart:{userId}` |

## 4. Cách chạy

### 4.1. Chạy local với Maven (profile `dev` — khuyến nghị)

App chạy trên máy; chỉ bật **hạ tầng** bằng Docker (MySQL, Redis, RabbitMQ).

```powershell
# 1) Infra cart (MySQL :3310, Redis :6379)
cd mircoservice/bookstore-cart-service
docker compose up -d cart-db cart-redis

# 2) RabbitMQ (saga) — từ Plan-And-Document
cd ../../Plan-And-Document
docker compose -f docker-compose.dev.yml up -d rabbitmq

# 3) Chạy cart-service local, profile dev
cd ../mircoservice/bookstore-cart-service
./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

- **Swagger:** `http://localhost:8080/swagger-ui.html` (port 8080 khi chạy Maven, không phải 8083)
- Cấu hình chi tiết: `src/main/resources/application-dev.yaml`

### 4.2. Chạy app trong Docker (profile `docker`)

Dùng khi cần hot-reload trong container; **không** phải workflow dev saga mặc định.

```powershell
cd mircoservice/bookstore-cart-service
docker network create bookstore-network 2>$null
docker compose up -d
docker compose logs -f cart-service
```

Service sẽ ở `http://localhost:8083`.

### 4.3. Chạy full stack bằng `docker-compose.dev.yml`

```powershell
cd mircoservice/bookstore-cart-service
docker compose -f docker-compose.dev.yml up -d
```

## 5. Curl test mẫu (gọi thẳng cart-service)

UUID mẫu:
- `USER_ID=11111111-1111-1111-1111-111111111111`
- `BOOK_ID=22222222-2222-2222-2222-222222222222`

```bash
# 1) Lấy giỏ hàng
curl http://localhost:8083/api/v1/cart \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" -H "X-User-Role: ROLE_USER"

# 2) Thêm sản phẩm
curl -X POST http://localhost:8083/api/v1/cart/add \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" -H "X-User-Role: ROLE_USER" \
  -H "Content-Type: application/json" \
  -d '{"bookId":"22222222-2222-2222-2222-222222222222","quantity":2}'

# 3) Update quantity (itemId lấy từ response bước 2)
curl -X PUT http://localhost:8083/api/v1/cart/update/<BOOK_ID> \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" -H "X-User-Role: ROLE_USER" \
  -H "Content-Type: application/json" \
  -d '{"quantity":5}'

# 4) Xoá 1 item
curl -X DELETE http://localhost:8083/api/v1/cart/remove/<BOOK_ID> \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" -H "X-User-Role: ROLE_USER"

# 5) Xoá toàn bộ
curl -X DELETE http://localhost:8083/api/v1/cart/clear \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" -H "X-User-Role: ROLE_USER"

# 6) Snapshot cho checkout
curl http://localhost:8083/api/v1/cart/snapshot \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" -H "X-User-Role: ROLE_USER"

# 7) Count
curl http://localhost:8083/api/v1/cart/count \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" -H "X-User-Role: ROLE_USER"

# 8) Check book in cart
curl http://localhost:8083/api/v1/cart/check/22222222-2222-2222-2222-222222222222 \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" -H "X-User-Role: ROLE_USER"
```

## 6. Mã lỗi

| HTTP | code | Khi nào |
| --- | --- | --- |
| 200 | 200 | Thành công |
| 400 | 400 | Validation lỗi |
| 401 | 401 | Thiếu/sai `X-User-Id` (không phải UUID) |
| 404 | 404 | `bookId` không tồn tại hoặc `itemId` không có |
| 409 | 409 | Vượt tồn kho |
| 503 | 503 | `book-service` down/timeout |

## 7. Test

```powershell
./mvnw clean test
```

