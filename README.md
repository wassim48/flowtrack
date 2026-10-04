# FlowTrack

Jira-inspired issue and sprint management API.
Java 21, Spring Boot 3.5, Spring Security (OAuth2 Resource Server), PostgreSQL, Flyway, Keycloak, Docker Compose.

> Work in progress. Current status: authentication with Keycloak, secured API, database migrations, global error format.

## Run locally

1. Start Postgres and Keycloak:

       cd infrastructure
       docker compose up -d

2. Start the API:

       cd backend/flowtrack-api
       ./mvnw spring-boot:run

3. Useful URLs:
   - API: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - Health: http://localhost:8080/actuator/health
   - Keycloak admin: http://localhost:8081 (dev only: admin / admin)

Postgres is exposed on port **15432** (not 5432) to avoid conflicts with a local PostgreSQL install.

## Key decisions

- Modular monolith (one Spring Boot app, packages per module).
- Flyway owns the database schema; Hibernate only validates it.
- The API is stateless and validates JWTs signed by Keycloak.
- All errors share one JSON format with a stable `code`.

## Roadmap

Projects and roles, issues with workflow and optimistic locking, Redis cache, Kafka with transactional outbox, notifications, sprints, React frontend.
