# Loc Event Platform

[![Backend CI/CD](https://github.com/shmoonwalker/loc-event-platform/actions/workflows/backend-ci-cd.yaml/badge.svg)](https://github.com/shmoonwalker/loc-event-platform/actions/workflows/backend-ci-cd.yaml)
[![Data CI/CD](https://github.com/shmoonwalker/loc-event-platform/actions/workflows/data-ci-cd.yaml/badge.svg)](https://github.com/shmoonwalker/loc-event-platform/actions/workflows/data-ci-cd.yaml)
[![Frontend CI/CD](https://github.com/shmoonwalker/loc-event-platform/actions/workflows/frontend-ci-cd.yaml/badge.svg)](https://github.com/shmoonwalker/loc-event-platform/actions/workflows/frontend-ci-cd.yaml)

Loc is a platform for discovering local events in the Netherlands. It collects events from external sources, publishes the ones that meet its quality rules, and lets visitors search, filter and explore them.

**Live:** [loc.shadibuilds.dev](https://loc.shadibuilds.dev)

---

## Features

**For visitors**, no account needed:

- Search events by text, and filter them by city, category, tag, date range, time of day, and in person or online.
- A homepage with rails for tonight, this weekend, near you and online, above the full event list.
- Event pages with the address, map coordinates, images, organizers, every upcoming date and a weather forecast for in-person events.
- Organizer pages with their profile and upcoming events.

**For members:**

- Registration with email verification, login and logout.
- Save events and follow organizers (through the API).

**Behind the scenes:**

- Events are collected daily from RVO and Ticketmaster, normalized, tagged and checked against publication rules before anyone sees them.
- Events whose descriptions are long enough are tagged with Gemini.
- Forecasts are fetched for published in-person events.

---

## Technical highlights

- **Two backend applications, one database.** The data application owns ingestion (`catalog` schema) and publishes a read contract (`publication` schema). The product API only reads that contract and owns its own `app` schema. Each app runs its own Flyway history.
- **Stateless authentication with Spring Security.** Short-lived JWT access tokens, plus rotating refresh tokens in an `HttpOnly` cookie with reuse detection. Every route is denied unless opened explicitly.
- **Rate limiting with Redis** on registration, login, verification emails and session refresh.
- **Consistent errors.** Every error response is an RFC 9457 `ProblemDetail`.
- **Real integration tests.** Backend tests run against real PostgreSQL and Redis containers with Testcontainers.
- **CI/CD per application** with GitHub Actions, deployed with Docker Compose to a self-hosted server behind Cloudflare Tunnel.
- **API documentation** with OpenAPI and Scalar at `/api/docs`.

---

## Architecture

Loc consists of three applications:

| Application | Technology | Responsibility |
|---|---|---|
| [Backend](backend/README.md) | Java 25, Spring Boot | Product API: event discovery, user accounts, authentication, saved events, followed organizers |
| [Data](data/README.md) | Java 25, Spring Boot | Collects external events, normalizes and enriches them, publishes qualified events |
| [Frontend](frontend/README.md) | Angular, Tailwind CSS | User interface; talks only to the backend API |

Supporting infrastructure: PostgreSQL, Redis, RabbitMQ and S3-compatible object storage (Cloudflare R2).

```text
RVO, Ticketmaster → Data → PostgreSQL (publication) → Backend API → Frontend
                     │
                     └── raw files in Cloudflare R2
```

The [system architecture](docs/architecture/system-architecture.md) describes the components and their boundaries.

---

## Repository structure

```text
.
├── backend/      Spring Boot product API
├── data/         Data collection and publication application
├── frontend/     Angular application
├── deploy/       Production deployment script
├── docs/         Cross-system architecture documentation
├── scripts/      Local development scripts
└── screenshots/  Project screenshots
```

---

## Getting started

Requirements: Docker, Java 25 and Node.js.

1. Copy `.env.example` to `.env` and fill in the values.
2. Start the local infrastructure:

   ```bash
   docker compose up -d db redis rabbitmq mailpit
   ```

3. Start the applications by following their guides:
   - [data](data/README.md): fills the `publication` schema; run it at least once to have events
   - [backend](backend/README.md): needs a `JWT_SECRET`, see its guide
   - [frontend](frontend/README.md)

| Service | Local address |
|---|---|
| Frontend | http://localhost:4200 |
| Backend API | http://localhost:8080/api |
| API documentation | http://localhost:8080/api/docs |
| Mailpit (local email inbox) | http://localhost:8025 |
| RabbitMQ management | http://localhost:15672 |

---

## Documentation

- [System architecture](docs/architecture/system-architecture.md)
- [Data architecture](docs/architecture/data-architecture.md)
- [Authentication](docs/architecture/authentication.md)
- [Backend guide](backend/README.md), [Discovery API](backend/docs/discovery.md) and [Member API](backend/docs/member.md)
- [Data guide](data/README.md)
- [Frontend guide](frontend/README.md)
- [Development scripts](scripts/README.md)
- [CI/CD workflows](.github/workflows/README.md)
- [Production deployment](deploy/README.md)

---

## Credits

Loc started as the HackYourFuture Class 55 Group A final project and is now independently designed and maintained.
