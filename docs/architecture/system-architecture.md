# System Architecture

## Purpose

This document defines the high-level architecture for the personal redesign of Loc.

It describes the major system components, ownership boundaries, data flow, and architecture principles.

Implementation details, delivery steps, source-specific ingestion logic, database schemas, queue definitions, and feature-specific designs are intentionally kept out of this document. Those belong in focused design documents and implementation tickets.

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
              │ Catalog / data-owned data     │
              │ Product / backend-owned data  │
              └──────────────┬────────────────┘
                             │
                      read published catalog
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
        │ Cache    │  │ Async work │
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
| **Backend API** | Public/product HTTP API. Reads the published event catalog. Owns product and user operations, security, caching, and product-side asynchronous messaging. |
| **Data worker** | Scheduled external data collection. Fetches source data, stores raw payloads, processes data, and publishes catalog data into PostgreSQL. It exposes no public product API. |
| **Frontend** | User interface. Communicates only with the backend API over HTTPS. |
| **PostgreSQL** | System of record for published catalog data and product/application data. One database with clear logical ownership boundaries. |
| **Object storage** | Durable storage for raw external payloads before processing and publication. |
| **Redis** | Disposable cache and temporary derived state. Never the system of record. |
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
- Redis caching
- product-side RabbitMQ messaging
- admin moderation

The backend owns writes to product-owned and user-owned data.

The backend does **not** publish ingestion-owned catalog data.

### Frontend

The frontend owns the user interface and communicates only with the backend API.

### Shared Contracts

The data and backend applications share only the contracts required to interact safely, primarily:

- published catalog structure
- identifiers and shared domain concepts
- optional asynchronous event or signal definitions

A shared Java library should only be introduced later if real duplication or contract-management needs justify it.

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

The backend may read published catalog data.

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

Only processed and published catalog data becomes product-facing.

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
Optional Redis cache
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
| **Member** | Standard member capabilities exposed through the backend API. |
| **Admin** | Human moderation and administrative capabilities exposed through the backend API. Admin actions do not replace or own the external catalog publication pipeline. |

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
        └── data-architecture.md
```

The frontend can remain unchanged until its redesign begins.

A shared library should not be introduced unless a concrete need appears.

---

## Architecture Principles

- Backend and data are independently runnable Java applications.
- PostgreSQL is the system of record for published catalog and product data.
- Object storage retains raw external payloads.
- Redis contains disposable cache or temporary state.
- RabbitMQ carries asynchronous work and events, not authoritative state.
- Data owns catalog publication.
- Backend owns product and user operations.
- Backend may read the published catalog but does not own ingestion publication.
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
- Backend architecture — add when the product API needs a focused design

```text
docs/architecture/
├── system-architecture.md
├── data-architecture.md
└── backend-architecture.md
```

Application-specific operational instructions belong in the corresponding application README, for example:

- `data/README.md` — how to build, configure, test, and run the data worker
- `backend/README.md` — how to build, configure, test, and run the backend
