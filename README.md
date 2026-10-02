# Loc Event Platform

Loc is a local-events platform for discovering events and interacting with them through a single product.

This repository is the personal continuation and redesign of the original HackYourFuture Class 55 Group A final project.

## Current Direction

The platform is being redesigned around three clear application areas:

- **Backend** — Java Spring Boot product API
- **Data** — separate Java worker for external event collection and catalog publication
- **Frontend** — user interface communicating only with the backend API

Supporting infrastructure includes PostgreSQL, S3-compatible object storage, Redis, and RabbitMQ.

For the high-level technical design, see:

**[System Architecture](docs/architecture/system-architecture.md)**

## Repository Structure

```text
.
├── backend/      Spring Boot product API
├── data/         Data application / ingestion work
├── frontend/     Frontend application
├── docs/         Cross-system technical documentation
└── screenshots/  Project screenshots
```

## Documentation

- [System Architecture](docs/architecture/system-architecture.md)
- [Data Architecture](docs/architecture/data-architecture.md)
- [Backend Guide](backend/README.md)
- [Data Guide](data/README.md)
- [Frontend Guide](frontend/README.md)

More focused architecture documents will be added only when a part of the system needs a detailed design.

## Project Status

The data worker now collects RVO and Ticketmaster events and publishes qualified
occurrences through the `publication` schema. The old backend implementation
has been removed; a small Spring Boot shell retains security and OpenAPI
configuration for the redesign. The frontend currently shows a holding page.

Current redesign priorities include:

- rebuilding the backend product API and its own application schema
- redesigning the frontend, admin, and user experiences

Discovery UX: grouped event cards on the home list and a full date/time list on event detail ([backend guide](backend/README.md)).

## Background

Loc started as the HackYourFuture Class 55 Group A final project.

The current repository keeps the useful parts of that foundation while evolving the project into an independently designed and maintained platform.
