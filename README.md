# Loc Event Platform

Loc is a platform for discovering local events in the Netherlands. It collects events from external sources, publishes the ones that meet its quality rules, and lets visitors search, filter and explore them.

Loc started as the HackYourFuture Class 55 Group A final project and is now independently designed and maintained.

## Architecture

Loc consists of three applications:

| Application | Technology | Responsibility |
|---|---|---|
| [Backend](backend/README.md) | Java 25, Spring Boot | Product API: event discovery, user accounts, authentication, saved events, followed organizers |
| [Data](data/README.md) | Java 25, Spring Boot | Collects external events, normalizes and enriches them, publishes qualified events |
| [Frontend](frontend/README.md) | Angular, Tailwind CSS | User interface; talks only to the backend API |

Supporting infrastructure: PostgreSQL, Redis, RabbitMQ and S3-compatible object storage (Cloudflare R2).

```text
External sources → Data → PostgreSQL (publication) → Backend API → Frontend
```

The [system architecture](docs/architecture/system-architecture.md) describes the components and their boundaries.

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

## Getting started

Requirements: Docker, Java 25 and Node.js.

1. Copy `.env.example` to `.env` and fill in the values.
2. Start the local infrastructure:

   ```bash
   docker compose up -d db redis rabbitmq
   ```

3. Start the applications by following their guides: [data](data/README.md), [backend](backend/README.md) and [frontend](frontend/README.md).

## Documentation

- [System architecture](docs/architecture/system-architecture.md)
- [Data architecture](docs/architecture/data-architecture.md)
- [Authentication](docs/architecture/authentication.md)
- [Backend guide](backend/README.md), [Discovery API](backend/docs/discovery.md) and [Member API](backend/docs/member.md)
- [Data guide](data/README.md)
- [Frontend guide](frontend/README.md)
- [Production deployment](deploy/README.md)
