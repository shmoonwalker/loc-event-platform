# Backend Architecture

## Purpose

The Loc backend is the product API between the frontend and the event catalog.

It serves public event discovery and owns application-specific behaviour such as users, saved events, comments, moderation and administration.

The backend does not collect or normalize external event data.

**Implementation status:** public discovery is implemented: homepage rails (only
rails with events are returned), event search/filtering/sorting/pagination, filter
options, and city suggestions. PostgreSQL reads use the qualified publication
contract. Event detail, authentication, saved events, comments, moderation, and
the backend-owned schema remain future work. The sections below include those
planned product responsibilities; they are not all implemented endpoints.

- [Discovery API](docs/discovery.md): endpoints, homepage behavior, filters,
  response shapes, and implementation notes.


---

## System Boundary

```text
External Event Sources
        ↓
Data Application
        ↓
Published Event Catalog
        ↓
Backend API
        ↓
Frontend
```

The frontend communicates only with the backend API.

The data application and backend have separate responsibilities and data ownership.

---

## Responsibilities

The backend is responsible for:

- public event discovery
- event search and filtering
- event detail
- organizer pages
- related event discovery
- authentication and user accounts
- saved events
- comments
- administration and moderation
- application-owned state

The backend is not responsible for:

- collecting events from external sources
- parsing provider-specific event data
- normalizing external events
- enriching external event data
- publishing or modifying catalog-owned event data

These responsibilities belong to the data application.

---

## Event Catalog

The data application owns event collection, the permissive normalized catalog,
and final publication. The backend's event read contract is the qualified,
enriched data in the `publication` schema, not the intermediate `catalog` schema.

Catalog data includes:

- events
- organizer records in the publication payload (`locOrganizerId` UUID, name, optional description and site)
- categories
- tags
- event relationships required by the catalog

The backend treats catalog data as read-only.

Changes to catalog data are made through the data application rather than through backend product APIs.

Data retains snapshots of previously published occurrences after they end or
are withdrawn. The backend can use those snapshots for event detail and
user-owned historical references. Events that never qualified for publication
have no public detail snapshot.

---

## Public Event Discovery

Anonymous users can browse and search published events without creating an account.

Public discovery can use catalog attributes such as:

- search text
- category
- tags
- location
- date and time

Public discovery reads `publication.discoverable_events`, which contains only
currently qualified occurrences. Backend-owned moderation visibility rules are
planned alongside the future moderation feature.

Publication eligibility and temporal filters exclude ended or withdrawn occurrences.
Backend-owned administrative hiding is planned, not yet implemented.

Discovery list results are grouped by `loc_event_id`: one card per logical event.
Each card selects the earliest matching occurrence and exposes title, start/end,
place, city, venue, and optional image. Categories/tags are filterable but are not
card fields; the complete vocabulary comes from `/api/events/filter-options`.
Pagination and totals count distinct events, not occurrence snapshots. Occurrence
counts and richer detail are not part of the current card response.

Occurrence-level filters (such as a date range or "this weekend") are applied to occurrences first. Results are then collapsed to events that retain at least one matching occurrence. The card's summary scheduling must reflect only the matched occurrences, not every occurrence of the event.

If some occurrences are withdrawn or cancelled but at least one remains discoverable, the event still appears as one card. Partial discoverability is reflected on the detail page, not by duplicating cards on the list.

Weather on grouped list cards must never blend forecasts across dates. By default, weather is omitted from the list card and shown per date on event detail. If a list card shows weather, it may only be the next occurrence's forecast and only when that date falls inside the data pipeline's forecast window.

Occurrence-level rows in `publication.discoverable_events` remain the storage truth; grouping is computed in the product API.

---

## Event Detail

An event detail page provides the available public information for a single event.

Event detail is keyed by `loc_event_id`. The page shows shared event information once and lists all relevant occurrences: upcoming discoverable times plus, where policy allows, past or cancelled occurrences still available through `publication.event_snapshot`. Each occurrence entry includes its `loc_occurrence_id`, schedule, state, and optional weather for that date when the data pipeline attached it. The primary entry from the discovery list is the event id; a deep link to a single occurrence may be supported as a secondary route.

An occurrence that was previously published may remain directly accessible
after it has ended or been cancelled through `publication.event_snapshot`.

The API should expose its current state clearly so the frontend can communicate that the event is past or cancelled.

Administratively hidden events are not publicly accessible.

---

## Organizers

Organizer identity is a data-owned publication contract. Each published
occurrence includes organizer records with a stable `locOrganizerId` UUID (the
same type as `loc_event_id` / `loc_occurrence_id`), the
display name, and optional `description` and `site` when the source sent them.
`publication.organizer_identity` reuses the same Loc UUID for the same
source and source organizer key (Ticketmaster promoter id, or the RVO
organizer name). A source-backed site or URL may be exposed when the source
provides it; the backend must not infer one from the event URL or organizer name.

An organizer page can show the stable ID, name, optional description and site,
and published events associated with that organizer. Further profile details are
outside the current data contract.

Organizer accounts, organizer authentication and organizer-managed pages are not part of the current product design.

---

## Related Events

The planned backend will provide related-event discovery using available catalog information such as:

- categories
- tags
- location
- schedule
- other relevant event attributes

The exact similarity and ranking implementation may evolve independently of the public product behaviour.

---

## Authentication and Users

User registration and JWT-based authentication are planned; they are not implemented in the current discovery API.

Authentication is not required for public event discovery.

Authenticated users can access account-specific functionality including:

- saving events
- viewing their saved events
- commenting on events

User-specific state belongs to the backend.

---

## Saved Events

Saving an event creates backend-owned user state referencing a stable published
Loc event or occurrence identity. The choice of event versus occurrence for
each user action belongs to the backend design.

Saved events remain available in the user's account even when the event later becomes:

- past
- cancelled

The event's current status should still be visible to the user.

Saving an event does not modify the catalog event itself.

---

## Comments

Authenticated users can comment on events.

Comments and their moderation state are owned by the backend.

Comment functionality is separate from catalog ownership and does not modify event data.

---

## Administration

Administrators use a separate administration interface from normal users.

Administrative behaviour can include:

- responding to platform messages
- moderating comments
- reviewing reports
- hiding an event from the Loc product when necessary

Administrative actions that affect visibility do not modify the source catalog record.

For example, hiding an event is backend-owned state keyed by `loc_event_id`
without modifying catalog rows or publication source snapshots.

Product hiding for discovery list purposes applies at `loc_event_id` unless a future design explicitly supports hiding individual occurrences.

---

## Data Ownership

The platform separates catalog data from application data.

### Data application owns

- external event collection
- normalization
- enrichment
- event publication
- organizers
- categories
- tags
- catalog event state

### Backend owns

- users
- authentication
- saved events
- comments
- reports
- moderation
- administrative product state

Notifications are planned, but their triggers, channels, and persistence model
have not been decided. Attendance or "going" is not part of the current backend
scope.

---

## PostgreSQL

Loc uses one PostgreSQL database with separate ownership boundaries.

Conceptually:

```text
PostgreSQL
├── catalog
│   └── intermediate normalized data owned by the data application
├── publication
│   └── qualified product read contract owned by the data application
│
└── backend-owned schema (to be created)
    └── owned by the redesigned backend
```

The data application has write access to catalog data.

The backend reads qualified publication data through JdbcClient. It will have
read/write access to its own application data when that schema is created. Product
event reads do not rely on intermediate catalog rows.

This keeps service responsibilities separate without requiring separate databases.

---

## RabbitMQ

RabbitMQ is used for asynchronous work where components need to react to events or changes.

It is not the source of truth for the event catalog and is not required for normal event reads.

Normal product reads follow:

```text
Backend
   ↓
Qualified Publication
```

RabbitMQ may later carry messages such as catalog changes when asynchronous backend behaviour needs them.

---

## Backend Structure

The backend is organized primarily by feature.

Conceptually:

```text
backend
├── event
├── organizer
├── category
├── user
├── comment
├── admin
└── config
```

Each feature owns the controller, business logic, persistence and DTOs required for that area.

Cross-cutting infrastructure belongs outside individual product features.

---

## API Principles

Backend APIs should:

- expose product behaviour rather than provider-specific external data
- keep HTTP concerns in controllers
- keep business rules in services
- keep persistence concerns in repositories
- use DTOs as the public API contract
- validate input at the API boundary
- return explicit event state where it affects product behaviour
- preserve clear ownership between catalog data and backend-owned state

The generated OpenAPI specification documents the implemented discovery endpoints.
See the linked API guide for examples and product semantics. Authentication
endpoints and their security scheme remain future work.

This document defines high-level backend behaviour and ownership rather than individual endpoint implementation.

---

## Documentation Rule

High-level documentation should describe stable product behaviour and ownership boundaries.

Implementation-specific details should only be documented when they are actively maintained.
