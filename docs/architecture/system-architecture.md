# System Architecture

## Purpose

This document describes the high-level architecture of Loc: the major components, their ownership boundaries, the data flow, and the principles behind them.

Source-specific ingestion logic, database schemas, queue definitions and feature designs are kept out of this document. They are described in the focused documents listed at the end.

---

## Goal

The system is divided into clear application and data responsibilities:

- **Backend** — Java Spring Boot product API
- **Data** — Java worker for external data collection and catalog publication
- **Frontend** — UI application that communicates only with the backend API

The architecture should support multiple external event sources over time without coupling external collection logic to normal product requests.

---

## Architecture

```text
                     External sources
                            │
                            ▼
                 ┌────────────────────┐
                 │ Data worker        │
                 │ Java / scheduled   │
                 └─────────┬──────────┘
                           │
                     store raw payloads
                           ▼
                 ┌────────────────────┐
                 │ Object storage     │
                 │ S3-compatible      │
                 └─────────┬──────────┘
                           │
                     process / publish
                           ▼
              ┌───────────────────────────────┐
              │ PostgreSQL                    │
              │                               │
              │ catalog: normalized data      │
              │ publication: product snapshots│
              │ backend-owned product data    │
              └──────────────┬────────────────┘
                             │
                      read publication snapshots
                             ▼
                    ┌─────────────────┐
                    │ Backend API     │
                    │ Spring Boot     │
                    └───────┬─────────┘
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
        ┌──────────┐  ┌────────────┐  Product /
        │ Redis    │  │ RabbitMQ   │  user ops
        │ Limits   │  │ Async work │
        └──────────┘  └─────▲──────┘
                            │
                    optional events /
                        signals
                            │
                      Data worker

                            ▲
                            │ HTTPS
                            │
                    ┌───────┴────────┐
                    │ Frontend       │
                    └────────────────┘
```

The frontend communicates only with the backend API.

It does not communicate directly with PostgreSQL, RabbitMQ, Redis, object storage, or external event sources.

---

## Components

| Component | Responsibility |
|---|---|
| **Backend API** | Public/product HTTP API. Reads qualified `publication` snapshots. Owns product and user operations, security, rate limiting, and product-side asynchronous messaging. |
| **Data worker** | Scheduled external data collection. Fetches source data, stores raw payloads, processes data, and publishes catalog data into PostgreSQL. It exposes no public product API. |
| **Frontend** | User interface. Communicates only with the backend API over HTTPS. |
| **PostgreSQL** | System of record for published catalog data and product/application data. One database with clear logical ownership boundaries. |
| **Object storage** | Durable storage for raw external payloads before processing and publication. |
| **Redis** | Temporary state such as rate-limit counters. Never the system of record; losing it loses no data. |
| **RabbitMQ** | Asynchronous work and integration events. Not authoritative storage. |
| **Scheduler** | Starts data collection runs in the data worker. |

---

## Ownership Boundaries

### Data

The data application owns:

- external source collection
- raw payload storage
- processing of collected data
- catalog publication
- ingestion pipeline operations

The data worker owns writes to ingestion-owned and catalog-owned data.

### Backend

The backend owns:

- HTTP API
- authentication and security
- user and product data
- reading the published catalog
- rate limiting with Redis
- product-side RabbitMQ messaging
- admin moderation

The backend owns writes to product-owned and user-owned data.

The backend does **not** publish ingestion-owned catalog data.

### Frontend

The frontend owns the user interface and communicates only with the backend API.

Discovery UX treats `loc_group_id` as the list unit (one card per show) and `loc_occurrence_id` as the schedule unit on event detail. The backend discovery API performs grouping; the frontend does not read `publication` directly.

### Shared Contracts

The data and backend applications share only the contracts required to interact safely, primarily:

- published catalog structure
- identifiers and shared domain concepts
- optional asynchronous event or signal definitions

Published snapshots remain occurrence-granular in `publication`; the backend may expose an event-granular discovery list DTO derived from those snapshots. Identifier semantics: `loc_group_id` is the show (list unit and public event id); `loc_event_id` is one source event and stays stable; `loc_occurrence_id` is one dated instance (schedule unit). See the [backend guide](../../backend/README.md) for discovery grouping rules.

There is no shared Java library between the applications. One is only introduced if real duplication or contract-management needs justify it.

---

## PostgreSQL Ownership

A single PostgreSQL database is used.

Ownership is logically separated:

```text
PostgreSQL
├── Catalog / ingestion-owned data
│   └── written by Data
│
└── Product / user-owned data
    └── written by Backend
```

The backend reads qualified data from `publication`; `catalog` is the data
worker's intermediate normalized store.

The data worker should not modify backend-owned product or user data.

This ownership boundary is architectural. It does not require separate databases.

---

## Raw and Published Data

Raw external data and product-facing catalog data have different purposes.

```text
External source
      ↓
Raw payload
      ↓
Object storage
      ↓
Data processing
      ↓
Published catalog
      ↓
PostgreSQL
      ↓
Backend API
      ↓
Frontend
```

Raw external payloads are not consumed directly by the product API.

Only qualified publication snapshots become product-facing.

---

## Data Flow

The normal data flow is:

```text
Schedule
   ↓
Fetch external source
   ↓
Store raw payload
   ↓
Process
   ↓
Publish catalog to PostgreSQL
   ↓
Optional RabbitMQ event / signal
   ↓
Backend reads published catalog
   ↓
Frontend
```

External collection or catalog publication must not run inside normal user HTTP requests.

---

## Messaging

RabbitMQ is used for asynchronous communication and background work.

It may support:

- product-side asynchronous work
- communication between applications
- optional events such as catalog publication completion

RabbitMQ does not contain authoritative application or catalog state.

Catalog correctness must not depend on a RabbitMQ message being delivered.

The authoritative published state exists in PostgreSQL.

---

## Roles

| Role | Meaning |
|---|---|
| **User** | A registered account (`USER`): standard member capabilities exposed through the backend API. |
| **Admin** | An account with the `ADMIN` role: human moderation and administrative capabilities exposed through the backend API. Admin actions do not replace or own the external catalog publication pipeline. |

---

## Repository Structure

The applications remain in one repository with explicit boundaries.

```text
/
├── backend/
│   └── Spring Boot product API
│
├── data/
│   └── Java data worker
│
├── frontend/
│   └── Frontend application
│
└── docs/
    └── architecture/
        ├── system-architecture.md
        ├── data-architecture.md
        └── authentication.md
```

---

## Architecture Principles

- Backend and data are independently runnable Java applications.
- PostgreSQL is the system of record for published catalog and product data.
- Object storage retains raw external payloads.
- Redis contains only temporary state.
- RabbitMQ carries asynchronous work and events, not authoritative state.
- Data owns catalog publication.
- Backend owns product and user operations.
- Backend reads qualified publication snapshots but does not own publication.
- Data does not write backend-owned user or product data.
- Frontend communicates only with the backend API.
- Raw external payloads are never directly exposed as the product catalog.
- External collection and publishing never run inside normal user HTTP request paths.
- Infrastructure may evolve without changing these ownership boundaries.

---

## Related Design Documents

This document intentionally stays high-level.

Focused architecture documents:

- [Data Architecture](data-architecture.md) — data worker runtime, pipeline, sources, catalog contract, and publication
- [Authentication](authentication.md) — registration, password storage, and rate limiting
- [Backend guide](../../backend/README.md) — product API behaviour, ownership, and how to run it
- [Data guide](../../data/README.md) — how to build, configure, test, and run the data worker
- [Frontend guide](../../frontend/README.md) — pages and deployment
