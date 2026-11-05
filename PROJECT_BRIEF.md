# Local Shops Marketplace: Project Brief

> **Instructions for the AI agent (read this first, every session)**
> This file is the single source of truth for the project. Read it fully before doing anything.
> Work on **one small task at a time**, in phase order. After each task: show what changed and why, explain any security or concurrency code, tell me how to test it, then **stop and wait for my next instruction**.
> Do not build features from later phases. Do not add dependencies without telling me why and asking first.
> I am a Java/Spring Boot developer at intermediate level. I have NOT learned Docker, Flyway, Redis, Testcontainers or CI pipelines, so do not use them. I am learning testing as I go, so explain each test you write.

---

## 1. Project overview

**Name:** Local Shops Marketplace
**What:** A multi-vendor marketplace backend where neighborhood shops list products and customers place orders.
**Goal:** A resume project targeting **Java backend developer** roles. Engineering depth matters more than UI polish. I must be able to explain every part of the code in an interview.

**Roles**
- `CUSTOMER`: browses products, places and tracks orders
- `SHOP_OWNER`: registers a shop, manages own products, handles incoming orders
- `ADMIN`: approves shops, manages users

---

## 2. Tech stack and constraints

**Use**
- Java 21, Maven, Spring Boot (latest stable 3.x)
- Spring Web, Spring Data JPA (Hibernate), Spring Security + JWT, Spring Validation
- PostgreSQL (installed locally)
- Lombok
- springdoc-openapi (Swagger UI) as the main interface
- JUnit 5 and Mockito (included with Spring Boot test starter)

**Do NOT use (for now)**
- Docker, Flyway, Redis, Testcontainers, GitHub Actions, Kafka/RabbitMQ, microservices
- Schema is generated from JPA entities (`ddl-auto: update` during development)

**Repo layout**
```
local-shops/
  backend/          Spring Boot project
  frontend/         optional, minimal, built last
  docs/             notes, ER diagram, decisions
  PROJECT_BRIEF.md  this file
  README.md
```

**Secrets:** never commit secrets. Read DB credentials and the JWT secret from environment variables (with safe local defaults for the DB only). `.env` files go in `.gitignore`.

---

## 3. Engineering rules (always follow)

1. Layered architecture: `controller -> service -> repository`. No business logic in controllers.
2. Never return JPA entities from APIs. Use DTOs (request and response classes).
3. Passwords hashed with BCrypt. Never log passwords, tokens or secrets.
4. Validate all request bodies with Bean Validation.
5. One global `@RestControllerAdvice` returning a consistent error format: `timestamp, status, error, message, path`.
6. Order placement must be `@Transactional` and use `@Version` optimistic locking on `Product` to prevent overselling.
7. Order creation must support an **idempotency key** so retried requests do not create duplicate orders.
8. All list endpoints are paginated.
9. Avoid N+1 queries. Check SQL logs when building list endpoints.
10. Use enums for roles and statuses. Use `BigDecimal` for money.
11. Package structure by layer under `com.localshops` (`controller`, `service`, `repository`, `model`, `dto`, `security`, `exception`, `config`).
12. Keep commits small with clear messages (`feat: ...`, `fix: ...`, `test: ...`, `docs: ...`).

---

## 4. Data model (starting point)

| Table | Fields |
|---|---|
| `users` | id, name, email (unique), password_hash, role, created_at |
| `shops` | id, owner_id (FK users), name, description, area, status (`PENDING`, `APPROVED`, `REJECTED`), created_at |
| `products` | id, shop_id (FK shops), name, description, price, stock, category, image_url, **version** (`@Version`), created_at |
| `orders` | id, customer_id (FK users), shop_id (FK shops), total, status, idempotency_key (unique per customer), created_at |
| `order_items` | id, order_id (FK orders), product_id (FK products), quantity, price_at_purchase |

Add indexes on frequently searched columns (`products.shop_id`, `products.category`, `products.name`, `shops.area`, `orders.customer_id`) using JPA `@Table(indexes = ...)`.

**Order status flow (only these transitions are valid)**
```
PLACED -> ACCEPTED -> PACKED -> DELIVERED
PLACED -> CANCELLED
ACCEPTED -> CANCELLED
```
Any other transition must be rejected with a clear error.

---

## 5. API outline (adjust as needed, keep REST conventions)

**Auth**
- `POST /api/auth/signup`
- `POST /api/auth/login` returns JWT
- `POST /api/auth/refresh` (stretch goal)

**Shops**
- `POST /api/shops` (SHOP_OWNER) register shop, starts as PENDING
- `GET /api/shops` (public) approved shops, filter by `area`, paginated
- `GET /api/shops/{id}` (public)
- `PATCH /api/admin/shops/{id}/approve` and `/reject` (ADMIN)

**Products**
- `POST /api/shops/{shopId}/products` (owner of that shop only)
- `PUT /api/products/{id}` and `DELETE /api/products/{id}` (owner only)
- `GET /api/products` (public) search by `q`, filter by `category`, `area`, `shopId`, price range, paginated and sortable
- `GET /api/products/{id}` (public)
- `POST /api/products/{id}/image` (owner only)

**Orders**
- `POST /api/orders` (CUSTOMER) body: items; header: `Idempotency-Key`
- `GET /api/orders/my` (CUSTOMER) paginated
- `GET /api/shops/{shopId}/orders` (owner of that shop) paginated
- `PATCH /api/orders/{id}/status` (owner moves it forward; customer can cancel while PLACED)

---

## 6. Build plan

Tick the boxes as tasks finish. Always work on the **current phase** only.

**Current phase: 3**

### Phase 1: Setup (Week 1)
- [x] Generate Spring Boot project in `/backend` (Web, Data JPA, PostgreSQL Driver, Security, Validation, Lombok)
- [x] Add springdoc-openapi dependency
- [x] `application.properties`: PostgreSQL datasource (`jdbc:postgresql://localhost:5432/localshops`, user/password from env vars with defaults `shops`/`shops`), `ddl-auto: update`, `open-in-view: false`, SQL logging on for development
- [x] `.gitignore` (Java, Maven, IDE, Node, `.env`)
- [x] Create `README.md` stub and `docs/` folder
- [x] App starts and connects to the database (pending manual DB user creation)
- [x] First commit: `chore: project setup`

*My manual prerequisite:* PostgreSQL installed, database `localshops` and user `shops` created, with `GRANT ALL ON SCHEMA public TO shops;`

### Phase 2: Auth and security (Weeks 2-3)
- [x] `User` entity, `Role` enum, `UserRepository`
- [x] Signup endpoint with validation and BCrypt
- [x] JWT utility (generate, validate) with secret from env var
- [x] Login endpoint returning JWT
- [x] JWT authentication filter and Spring Security config (stateless)
- [x] Role-based access on endpoints (`@PreAuthorize` or config)
- [x] Global exception handler with consistent error format
- [x] Swagger configured with a "Bearer token" Authorize button
- [ ] (Stretch) refresh tokens

*Done when:* I can register, log in, call a protected endpoint with the token, and get 401/403 where expected.

### Phase 3: Catalog APIs (Weeks 3-5)
- [ ] `Shop` entity, registration (PENDING), admin approve/reject
- [ ] `Product` entity with `@Version`, owner-only CRUD (check ownership in the service layer)
- [ ] Public product search and filters with pagination and sorting
- [ ] Image upload to local folder, serve via URL
- [ ] DTOs and mapping for everything
- [ ] Swagger documentation with clear descriptions

*Done when:* the whole catalog works through Swagger.

### Phase 4: Orders and consistency (Weeks 5-7)
- [ ] `Order` and `OrderItem` entities
- [ ] Place-order service: validate items, check stock, reduce stock, calculate total, all in one `@Transactional` method
- [ ] Handle `ObjectOptimisticLockingFailureException` (retry a limited number of times, then return a clear conflict error)
- [ ] Idempotency key handling (same key + same customer returns the original order)
- [ ] Order status state machine with validation of transitions
- [ ] Customer order history and owner incoming orders (paginated)
- [ ] Orders must only contain products from one shop, reject otherwise

*Done when:* a full order lifecycle works and overselling is impossible.

### Phase 5: Performance basics (Weeks 7-8)
- [ ] Add JPA indexes (see data model)
- [ ] Review SQL logs and fix any N+1 queries (fetch joins or entity graphs)
- [ ] Seed a larger dataset (e.g. 10,000 products) with a small data-loader class (profile-based)
- [ ] Measure response times of the search endpoint before and after indexing, record numbers in `docs/performance.md`

### Phase 6: Basic testing (Weeks 8-9)
- [ ] Unit tests (JUnit 5 + Mockito) for the order service: success, insufficient stock, invalid status transition, idempotent retry
- [ ] Unit tests for auth service (signup duplicate email, login failure)
- [ ] One concurrency test: several threads try to buy the last item, only one succeeds
- [ ] Explain each test to me in comments or in your reply

### Phase 7: Ship it (Weeks 9-11)
- [ ] Deploy backend and PostgreSQL to a platform that builds Spring Boot from a GitHub repo (check current docs of Railway or Render)
- [ ] Production config via environment variables
- [ ] Demo data seeding with test accounts: one admin, one shop owner, one customer
- [ ] README: description, live link, tech stack, architecture diagram, ER diagram, how to run locally, key technical decisions, API overview, screenshots
- [ ] Postman collection exported into `docs/`
- [ ] (Optional) minimal UI, see section 7

---

## 7. Optional minimal UI (only after Phase 6, and only if time allows)

Backend is the priority. Swagger is the main interface. If I ask for a UI:

- Use **Stitch** for design and the **Stitch MCP server** in Antigravity to pull screens.
- Keep it to 5 screens: login/signup, product listing with search, product detail + cart, checkout + order history, shop owner page.
- Use **plain HTML, Tailwind via CDN, and vanilla JavaScript** (no React, I have not learned it).
- Put all API calls in one `api.js` that attaches the JWT. Handle loading, error and empty states.
- Enable CORS in Spring Boot for the frontend origin.
- Do not redesign Stitch screens yourself. Editing screens through the MCP may create duplicates instead of updating, so avoid unnecessary edits.
- Never commit the Stitch API key.

---

## 8. Interview story checklist (what this project should let me explain)

1. How optimistic locking prevents overselling, and what happens on a conflict
2. How idempotency keys prevent duplicate orders
3. How the order status state machine rejects invalid transitions
4. How JWT authentication and role-based authorization work in my app
5. How indexing and fixing N+1 queries improved performance (with real numbers)
6. How my tests prove the above

---

## 9. First task for the agent

Start with **Phase 1 only**:

1. Create the repo structure from section 2.
2. Generate the Spring Boot backend with the Phase 1 dependencies.
3. Configure `application.properties` and `.gitignore`.
4. Run the app and confirm it connects to my local PostgreSQL. If the connection fails, tell me what to fix instead of changing the design.
5. Make the first commit.
6. Summarize what you created, tick the Phase 1 boxes in this file, and **stop and wait**.

