# Backend

The Loc backend is the product API between the frontend and the event catalog. It is a Spring Boot application (Java 25) that serves public event discovery, user registration, and in the future all other user-owned features.

The backend does not collect or normalize external event data. That is the job of the [data application](../data/README.md).

```text
External sources → Data application → publication schema → Backend API → Frontend
```

The frontend communicates only with the backend API.

---

## Features

| Area | Endpoints | Documentation |
|---|---|---|
| Event discovery | `GET /api/home`, `/api/events`, `/api/events/{id}`, `/api/events/filter-options`, `/api/cities`, `/api/organizers/{id}`, `/api/categories`, `/api/tags` | [Discovery API](docs/discovery.md) |
| Registration | `POST /api/auth/register` | [Authentication](../docs/architecture/authentication.md) |

Interactive API documentation (Scalar) is served at `/api/docs`, and the OpenAPI document at `/api/docs/openapi.yaml`.

---

## Running locally

Requirements: Java 25 and Docker.

1. Start PostgreSQL and Redis from the repository root:

   ```bash
   docker compose up -d db redis
   ```

2. Start the backend from `backend/`:

   ```bash
   ./mvnw spring-boot:run
   ```

   Add `-Dspring-boot.run.profiles=dev` for debug logging of `nl.loc.backend`.

The API runs on `http://localhost:8080`. On startup, Flyway creates or migrates the backend's `app` schema.

Discovery endpoints read the `publication` schema, which the data application creates and fills. Run the data application at least once against the same database to get events (see [data/README.md](../data/README.md)).

Redis is only needed for rate limiting. Without it the backend still starts and serves requests, but nothing is rate limited and a warning is logged per limited request.

---

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost`, `5432`, `POSTGRES_DB` or `project_db` | Database location |
| `DB_USER`, `DB_PASSWORD` | `POSTGRES_USER` / `POSTGRES_PASSWORD`, then `admin` / `password` | Database credentials |
| `REDIS_URL` | `redis://localhost:6379` | Redis for rate limiting; in production includes the password |

Rate limits are set under `app.rate-limit.policies` in `src/main/resources/application.yaml`.

---

## Code structure

The code is organized by feature. Each feature owns its controller, service, repository, model and DTOs.

```text
nl.loc.backend
├── config/        security chain, password encoder, error handling, OpenAPI, clock
│   └── ratelimit/ Redis-backed rate limiter
├── event/         public discovery: events, organizers, cities, categories, tags
└── user/          user accounts and registration
```

Within a feature:

| Package | Contains |
|---|---|
| `controller` | HTTP endpoints; no business rules |
| `service` | Business rules |
| `repository` | Persistence |
| `model` | Domain types and entities |
| `dto/request`, `dto/response` | The public API contract |

Conventions:

- Controllers validate input at the boundary (`@Valid`) and use DTOs, never entities, in the API.
- Every error is a `ProblemDetail`, produced by `config/GlobalExceptionHandler`.
- The security chain denies every route that is not explicitly opened in `config/SecurityConfig`.
- Time comes from the injected `Clock` (`config/TimeConfig`), so tests can fix it.

---

## Data ownership

Loc uses one PostgreSQL database with separate ownership per schema:

```text
PostgreSQL
├── catalog      intermediate normalized data, owned by the data application
├── publication  qualified product read contract, owned by the data application
└── app          application data, owned by the backend
```

- The backend **reads** `publication` through `JdbcClient` and never writes to it. Product reads never use the intermediate `catalog` schema.
- The backend **owns** `app`, migrated by its own Flyway history table `app.flyway_backend_history`. Entities in `app` use JPA.
- Tables in `app` that reference published events store `loc_event_id` without a foreign key into `publication`.

Published data keeps snapshots of occurrences after they end or are withdrawn, so user-owned references (such as saved events) can still show the event and its current state.

---

## Discovery behaviour

- Anonymous users can browse and search without an account.
- Lists are grouped by `loc_event_id`: one card per logical event, showing the earliest occurrence that matches the filters. Pagination and totals count events, not occurrences.
- Occurrence-level filters (dates, "this weekend") are applied to occurrences first; an event is shown when at least one occurrence matches.
- An event detail page shows the shared event information once and lists its upcoming occurrences, with per-date weather when the data application attached a forecast.
- Organizer identity comes from the data application (`locOrganizerId`, name, optional description and site). The backend never infers an organizer site from other fields.

Endpoint details, parameters and response shapes are in the [Discovery API](docs/discovery.md).

---

## Tests

Integration tests run against real PostgreSQL and Redis containers (Testcontainers), so Docker must be running.

```bash
./mvnw clean test checkstyle:check
```

CI runs Checkstyle and the tests on every pull request that changes `backend/`.

---

## Planned features

These features are part of the product design and are not built yet:

- **Login and tokens:** see the planned design in [Authentication](../docs/architecture/authentication.md).
- **Saved events:** backend-owned user state referencing a published Loc event or occurrence. Saved events stay visible when the event later becomes past or cancelled, together with its current state.
- **Comments:** authenticated users comment on events; comments and their moderation state belong to the backend.
- **Administration:** a separate admin interface for moderating comments, reviewing reports and hiding events. Hiding is backend-owned state keyed by `loc_event_id`; it never modifies catalog or publication data.
- **Related events:** suggestions based on categories, tags, location and schedule.
