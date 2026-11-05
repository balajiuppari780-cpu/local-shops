# Local Shops Marketplace

A multi-vendor marketplace backend where neighborhood shops list products and customers place orders.

## Tech Stack
- **Java 21**
- **Spring Boot 3.4.3**
- **Spring Web, Spring Data JPA, Spring Security, Spring Validation**
- **PostgreSQL**
- **springdoc-openapi (Swagger UI)**
- **Lombok**
- **JUnit 5 & Mockito**

## Project Structure
```
local-shops/
  backend/          Spring Boot project
  frontend/         Optional minimal UI (Phase 7)
  docs/             Architecture notes, ER diagrams, decisions
  PROJECT_BRIEF.md  Single source of truth and build plan
  README.md         Project overview and run instructions
```

## Getting Started

### Prerequisites
- JDK 21+
- Local PostgreSQL instance running on port 5432
- Database `localshops` and user `shops` created:
  ```sql
  CREATE USER shops WITH PASSWORD 'shops';
  CREATE DATABASE localshops OWNER shops;
  GRANT ALL PRIVILEGES ON DATABASE localshops TO shops;
  -- In PostgreSQL 15+, inside localshops database:
  \c localshops
  GRANT ALL ON SCHEMA public TO shops;
  ```

### Running Locally
```bash
cd backend
./mvnw spring-boot:run
```

API documentation (Swagger UI) will be available at:
`http://localhost:8080/swagger-ui.html`
