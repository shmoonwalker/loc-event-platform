# Backend Architecture

## Purpose

The Loc backend is the product API between the frontend and the event catalog.

It serves public event discovery and owns application-specific behaviour such as users, saved events, comments, moderation and administration.

The backend does not collect or normalize external event data.

**Implementation status:** the old controllers, services, repositories, and
database migrations have been removed. The current application is a Spring
Boot shell with OpenAPI metadata, request validation error handling, and a
default-deny security configuration. Product endpoints, authentication,
database access, and the backend-owned schema will be implemented during the
redesign. The sections below describe intended product behaviour.

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
currently qualified occurrences. The backend also applies any backend-owned
moderation visibility rules.

Past, cancelled or administratively hidden events are excluded from normal discovery and search results.

Discovery list results are grouped by `loc_event_id`: the home and default browse UI show one card per logical event, not one card per occurrence. A card represents an event that has at least one currently discoverable occurrence after backend moderation rules. The list response includes shared event fields (title, location, categories, tags, primary image) plus summary scheduling such as the next upcoming start and the count of upcoming discoverable occurrences, and optionally the last upcoming start or a short date range. Pagination and total counts refer to distinct events, not occurrence snapshots. Sorting (for example soonest) uses the earliest upcoming discoverable occurrence per event.

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

The backend provides related-event discovery using available catalog information such as:

- categories
- tags
- location
- schedule
- other relevant event attributes

The exact similarity and ranking implementation may evolve independently of the public product behaviour.

---

## Authentication and Users

Loc supports user registration and JWT-based authentication.

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

The backend will read qualified publication data and have read/write access to
its own application data when that schema is created. The current shell has no
database connection. Product event reads will not rely on intermediate catalog
rows.

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

As backend endpoints are implemented, their generated OpenAPI specification
will describe the actual request and response structures. The current shell
has no product endpoints or authentication scheme to document yet.

This document defines high-level backend behaviour and ownership rather than individual endpoint implementation.

---

## Documentation Rule

High-level documentation should describe stable product behaviour and ownership boundaries.

Implementation-specific details should only be documented when they are actively maintained.
