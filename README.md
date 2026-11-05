# FlowTrack

A Jira-inspired project and issue management backend built to explore production-oriented backend engineering with **Java 21**, **Spring Boot 3.5**, **PostgreSQL**, **Keycloak**, **Flyway**, and **Testcontainers**.

> **Status:** active work in progress. The backend currently implements authentication, local user synchronization, project membership, project-scoped issues, pagination, database migrations, validation, and automated tests. Planned features are listed separately in the roadmap.

## Highlights

- **Modular monolith** organized by business capability: `auth`, `users`, `projects`, `issues`, and `shared`.
- **Stateless API security** with Spring Security OAuth2 Resource Server and JWTs issued by Keycloak.
- **Identity synchronization** between Keycloak identities and the local PostgreSQL user model.
- **Project-level authorization** based on membership and project roles.
- **Transactional project creation** with automatic creator membership.
- **Project-scoped issue numbering** with a database uniqueness constraint.
- **Role-aware issue creation**: viewers can read project issues but cannot create them.
- **Paginated issue listing** with input validation and deterministic sorting.
- **Schema ownership with Flyway** while Hibernate validates the mapped schema.
- **Integration testing with PostgreSQL Testcontainers**, plus Spring Security test support.
- **OpenAPI / Swagger UI** and Spring Boot Actuator for API exploration and health checks.

## Tech stack

| Area | Technologies |
| --- | --- |
| Language | Java 21 |
| Backend | Spring Boot 3.5, Spring MVC, Spring Data JPA / Hibernate |
| Security | Spring Security, OAuth2 Resource Server, JWT, Keycloak 26 |
| Database | PostgreSQL 16, Flyway |
| API documentation | Springdoc OpenAPI / Swagger UI |
| Testing | JUnit 5, Spring Boot Test, Spring Security Test, Testcontainers |
| Infrastructure | Docker Compose, Maven Wrapper |
| Monitoring | Spring Boot Actuator |

## Architecture

FlowTrack currently follows a **modular monolith** approach: one deployable Spring Boot application, with code separated by business module rather than by technical layer alone.

```text
flowtrack/
├── backend/
│   └── flowtrack-api/
│       └── src/
│           ├── main/
│           │   ├── java/com/flowtrack/
│           │   │   ├── auth/
│           │   │   ├── users/
│           │   │   ├── projects/
│           │   │   ├── issues/
│           │   │   └── shared/
│           │   └── resources/
│           │       └── db/migration/
│           └── test/
└── infrastructure/
    ├── docker-compose.yml
    ├── keycloak/
    └── postgres/
```

Inside the larger business modules, responsibilities are separated into API, application, domain, and persistence packages where appropriate.

### Why a modular monolith?

The goal is to keep deployment and local development simple while maintaining clear module boundaries. This provides a foundation that can evolve without introducing distributed-system complexity before it is justified.

## Implemented functionality

### Authentication and users

- Keycloak-backed JWT authentication.
- `GET /api/me` exposes the authenticated local user.
- Keycloak identities are synchronized with the local user model.
- Unauthorized and forbidden responses use JSON error payloads.

### Projects

- Create a project with `POST /api/projects`.
- List projects visible to the authenticated user with `GET /api/projects`.
- The project creator is registered as a project member during project creation.
- Project access is restricted by membership.

### Issues

- Create an issue with `POST /api/projects/{projectId}/issues`.
- List project issues with pagination via `GET /api/projects/{projectId}/issues?page=0&size=20`.
- Retrieve a project issue via `GET /api/projects/{projectId}/issues/{issueNumber}`.
- Issue numbers are allocated within each project.
- Supported issue types: `TASK`, `BUG`, `STORY`.
- Supported statuses in the current schema: `TODO`, `IN_PROGRESS`, `DONE`.
- Supported priorities: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
- Project membership is required to access issues.
- Members with the `VIEWER` role cannot create issues.

## Data integrity and transaction design

FlowTrack intentionally keeps important invariants in both application logic and the database:

- Flyway version-controls the database schema.
- `(project_id, issue_number)` is unique at database level.
- Issue numbers must be positive.
- Issue type, status, and priority values are constrained in PostgreSQL.
- Project creation and creator membership are handled transactionally.
- Issue creation allocates the next project issue number inside a transaction.

These choices are intended to make invalid states harder to persist and to keep domain behavior explicit.

## Run locally

### Prerequisites

- Java 21
- Docker with Docker Compose

### 1. Start PostgreSQL and Keycloak

```bash
cd infrastructure
docker compose up -d
```

The development environment starts:

- PostgreSQL on `localhost:15432`
- Keycloak on `localhost:8081`

PostgreSQL intentionally uses port **15432** on the host to avoid conflicts with a local PostgreSQL installation.

### 2. Start the API

Linux / macOS:

```bash
cd backend/flowtrack-api
./mvnw spring-boot:run
```

Windows:

```powershell
cd backend\flowtrack-api
.\mvnw.cmd spring-boot:run
```

### 3. Useful local URLs

| Service | URL |
| --- | --- |
| API | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Health | `http://localhost:8080/actuator/health` |
| Keycloak | `http://localhost:8081` |

The default Keycloak administrator credentials in the local development setup are `admin / admin`. They are for local development only.

## Run the tests

Docker must be available because integration tests use PostgreSQL Testcontainers.

Linux / macOS:

```bash
cd backend/flowtrack-api
./mvnw test
```

Windows:

```powershell
cd backend\flowtrack-api
.\mvnw.cmd test
```

The current test suite covers user persistence/synchronization, project repository and service behavior, project API authorization, issue service behavior, issue API behavior, validation, access control, and database-backed integration scenarios.

## API documentation

When the application is running, Swagger UI is available at:

```text
http://localhost:8080/swagger-ui.html
```

This is the quickest way to inspect the current REST surface and request/response models.

## Engineering decisions

**Flyway owns the schema.** Database changes are explicit and versioned instead of being generated implicitly by Hibernate.

**PostgreSQL is used in integration tests.** Testcontainers reduces the gap between test behavior and the real database engine.

**Authorization is enforced server-side.** Project membership and role checks are applied in the application layer rather than relying on a future frontend.

**Errors have a consistent JSON representation.** Authentication, authorization, validation, and application errors are exposed through predictable API responses.

**Implemented features and planned features are kept separate.** The roadmap below describes direction only; it is not presented as completed functionality.

## Roadmap

- Issue update and workflow transitions
- Assignees and richer project membership management
- Optimistic locking for concurrent issue updates
- Sprint / backlog management
- Redis cache where measurements justify it
- Kafka events with a transactional outbox
- Asynchronous notifications / SSE
- React frontend
- CI/CD and observability improvements

## Project purpose

FlowTrack is a personal engineering project focused on practicing backend design decisions beyond basic CRUD: authentication, authorization, transactional consistency, relational constraints, modular boundaries, automated integration testing, and incremental architecture evolution.

---

**Author:** Wassim Lahdoudi  
Java / Spring Boot / React Software Engineer
