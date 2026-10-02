
# Data Architecture

## Purpose

The Loc data application collects external event data, preserves original
payloads, normalizes and enriches them in `catalog`, and publishes qualified
occurrences through `publication`.

The data application is a separate Java application from the main Spring Boot backend.

It is designed to support multiple external sources with different schemas, collection mechanisms, completeness levels, update behaviour, and reliability.

---

## Scope

The data application owns:

- external source integration
- scheduled data collection
- raw payload storage
- collection and processing state
- normalization
- enrichment
- validation
- event identity
- deduplication
- catalog publication
- AI-generated event tags
- replay and reprocessing

The backend owns product and user behaviour around published catalog data.

The backend does not fetch, normalize, or process external event sources.

---

## High-Level Architecture

```text
                     EXTERNAL SOURCES
                            │
                            ▼
                     ┌──────────────┐
                     │  Collection  │
                     └──────┬───────┘
                            │
             ┌──────────────┴──────────────┐
             ▼                             ▼
     ┌───────────────┐            ┌─────────────────┐
     │ Object Storage│            │ Collection State│
     │   Raw Data    │            │   PostgreSQL    │
     └───────┬───────┘            └─────────────────┘
             │
             ▼
      ┌──────────────┐
      │  Processing  │
      └──────┬───────┘
             │
             ├────────────────────► Processing State
             │                      PostgreSQL
             ▼
      ┌──────────────┐
      │ Local tags / │
      │ AI tagging   │
      └──────┬───────┘
             │
             ▼
      ┌──────────────┐
      │ Publication  │
      └──────┬───────┘
             │
             ▼
       ┌──────────────────────┐
       │ PostgreSQL           │
       │ Qualified snapshots  │
       └──────────┬───────────┘
                  │
                  ▼
              Backend API
```

---

## Data Flow

The processing pipeline follows one consistent sequence:

```text
Raw payload
    ↓
Parse
    ↓
Normalize
    ↓
Enrich
    ↓
Validate
    ↓
Identity / Deduplicate
    ↓
Local tags / optional async AI tagging
    ↓
Publish when qualified
```

This is the canonical processing order for the data application.

Basic malformed-payload checks may happen during parsing, but final event validation happens after normalization and enrichment.

---

## Collection

Collection communicates with external providers and preserves what they actually returned.

Its responsibility is:

> What exactly did the source return?

Collection does not convert source data into Loc events.

Sources may use different collection mechanisms.

Examples include:

```text
compressed bulk feeds
REST APIs
paginated APIs
JSON datasets
CSV feeds
partner integrations
approved scraping where legally permitted
```

Provider-specific collection behaviour is isolated from the rest of the pipeline.

---

## Raw Storage

Raw payloads are stored in S3-compatible object storage.

Loc currently uses Cloudflare R2 as the selected object-storage provider for both development and production. Development and production use separate private buckets.

The data application accesses object storage through the S3-compatible interface, so the pipeline is not tightly coupled to R2-specific storage APIs.

Raw payloads are immutable.

A new collection creates new raw objects instead of modifying previous ones.

Retention rules may differ between sources because licensing and provider terms may impose different requirements.

Raw objects are organized by source and collection run.

---

## Collection and Processing Are Separate

Collection and processing are intentionally separate workflows.

```text
External source
      ↓
Collection
      ↓
Raw data stored
      ↓
Collection state completed
      ↓
Processing
```

Processing does not require the external provider to still be available.

This allows previously collected payloads to be processed again when:

* normalization logic changes
* enrichment improves
* validation rules change
* category mapping changes
* deduplication logic changes

without fetching the source again.

---

## Collection Runs

Each collection attempt is represented conceptually by a collection run.

A collection run may record information such as:

```text
source
started at
completed at
status
raw payload location
number of payloads
provider metadata
```

The exact database schema is an implementation decision.

A failure in one external source should not prevent unrelated sources from collecting successfully.

---

## Processing Runs

Processing is tracked independently from collection.

A processing run may record:

```text
input collection run
started at
completed at
status
records read
records accepted
records rejected
records published
processing version
```

The same collection run may be processed multiple times.

PostgreSQL remains the authoritative state for whether collection or processing work exists, succeeded, failed, or needs further action.

---

## Source Adapters

External providers are isolated behind source-specific adapters.

Conceptually:

```text
SourceAdapter
├── RVO
├── Ticketmaster
└── future providers
```

Adapters handle source-specific concerns such as:

```text
authentication
pagination
bulk downloads
provider metadata
rate limits
source identifiers
source-specific collection behaviour
```

The rest of the processing pipeline should not depend on how the source was fetched.

---

## Current Sources

The implemented pipeline handles two external providers with different shapes
and collection styles:

| Source          | Role                                                                                    |
| --------------- | --------------------------------------------------------------------------------------- |
| RVO Events      | Government events (networking, workshops, learning)     |
| Ticketmaster NL | National event source with rich event, venue, classification, and lifecycle data        |

Additional sources can be added later through new adapters.

RVO and Ticketmaster collection and processing are implemented. See
`data/README.md` for their current commands and schedules.

---

## Collection Does Not Mean Publication

Collecting a source record does not automatically make it public. Catalog
import retains normalized source records permissively. A separate publication
decision qualifies each occurrence for the product, holds it for missing
requirements, or withdraws it when it no longer qualifies. Source mapping
rules stay in the source adapters; final product qualification is shared.

---

## Shared Loc Event Categories

Loc currently uses the following top-level event categories:

```text
Music & Nightlife
Arts & Culture
Sports
Business & Careers
Technology & Science
Learning & Skills
Nature & Sustainability
Other
```

These categories are intentionally broad.

More specific event meaning may later be represented through:

```text
subcategories
AI-generated tags
source classifications
```

The category vocabulary is a shared Loc contract.

The data application owns mapping external provider classifications into this vocabulary.

The backend consumes the same category set.

Changing the top-level category vocabulary is therefore a shared contract change between the data application and backend.

If an event cannot confidently be mapped into another category, it is mapped to:

```text
Other
```

Original provider classifications should still be preserved for future remapping or analysis.

---

## Canonical Event Representation

External sources are transformed into a common Loc event representation.

The first conceptual shape includes areas such as:

```text
Event

Identity
├── source
├── external identifier / source-local key
└── raw payload reference

Content
├── title
└── description?

Classification
├── Loc category
├── source classifications
└── source tags

Timing
├── start date/time
├── end date/time?
└── timezone / offset?

Location
├── type
├── venue?
├── address?
├── city?
├── country?
├── coordinates?
└── geometry?

Pricing
├── pricing type
├── minimum price?
├── maximum price?
├── currency?
└── pricing description?

Other
├── organizers?
│   ├── source identity (Ticketmaster promoter id, or RVO name)
│   ├── name
│   ├── description?
│   └── site? (source-provided location or website)
├── ticket / registration URL?
├── image URL?
├── source status?
├── source created timestamp?
└── source updated timestamp?
```

This is an architectural representation, not the final database schema.

Exact fields, tables, Java classes, and mappings belong to detailed implementation design.

---

## Missing Data

External event records are expected to be incomplete.

Missing optional values do not automatically make an event invalid.

Examples include:

```text
description
venue
address
city
coordinates
organizer
price
image
source status
```

The processing pipeline may attempt to enrich missing fields when a trustworthy source exists.

If a value still cannot be determined reliably, it remains unknown rather than being invented.

---

## Enrichment

Enrichment happens after normalization.

Possible enrichment sources include:

```text
official provider endpoints
official event detail pages
official organizer or venue information
trusted government geodata
approved partner integrations
```

Enrichment must be source-aware and reproducible.

The pipeline should not perform arbitrary internet searches and accept unverifiable values simply because a field is missing.

Failure to enrich optional data should normally not prevent publication.

---

## Location

An event location may conceptually be:

```text
PHYSICAL
ONLINE
UNKNOWN
```

A physical event may contain some combination of:

```text
venue
address
city
country
coordinates
geometry
```

Not every provider supplies every location field.

Trusted services may be used later to derive missing geographic information from known addresses or coordinates.

Unknown location does not cause the raw source record to be discarded.

Whether an event with unresolved location is publishable is determined by publication rules.

---

## Pricing

Pricing is optional.

Different providers represent price differently.

The architecture should support concepts such as:

```text
FREE
PAID
VARIABLE
UNKNOWN
```

with optional values such as:

```text
minimum price
maximum price
currency
pricing description
```

The exact pricing model belongs to implementation design.

---

## Provenance

Loc must preserve enough provenance to trace published data back to its source.

At minimum:

```text
source
source identifier
raw payload reference
source URL when available
source timestamps when available
```

Future enrichment may require more detailed provenance for individual values.

For example:

```text
city
→ derived from official address

price
→ obtained from provider detail endpoint
```

This allows Loc to understand where data came from and resolve disagreements between sources later.

---

## Event Identity and Idempotency

When a provider exposes a stable external identifier, same-source identity uses:

```text
source + externalId
```

This allows repeated imports from the same source to remain idempotent.

If a source does not provide a stable identifier, its adapter must derive a deterministic source-local key from stable source attributes.

This provides same-source idempotency without requiring cross-source fuzzy matching.

---

## Organizer Identity

Organizer identity is a data-owned publication contract, parallel to event
identity. Same-source organizer identity uses:

```text
source + external organizer key
```

- Ticketmaster: promoter id. The display name is not the identity.
- RVO: the organizer name as sent. Two different strings stay two organizers.

`publication.organizer_identity` mints one `locOrganizerId` UUID per source
identity — the same type as `loc_event_id` and `loc_occurrence_id` — and reuses
it on later imports. The same display name from Ticketmaster and from
RVO remains two organizers because the identity includes the source.

Catalog import stores source organizer facts on `catalog.event_organizer`.
Publication copies them onto each occurrence snapshot as records with:

```text
locOrganizerId   UUID, same type as loc_event_id
name
description?   (only when the source sent one)
site?          (only when the source sent a location or website)
```

A bare list of names is not the product contract. Organizer profile pages and
grouping events by organizer are backend concerns. The backend must not invent
an organizer URL from the event URL or name.

---

## Cross-Source Deduplication

Cross-source fuzzy deduplication is not part of the current pipeline.

The same real-world event may eventually appear in multiple providers.

Reliable cross-source matching may later use information such as:

```text
title
venue
location
date/time
organizer
source relationships
```

This should only be introduced after enough real multi-source data exists to design and evaluate reliable matching rules.

---

## Validation

Validation determines whether a normalized and enriched event is suitable for publication.

Some values may be required for all published events, while others remain optional.

Detailed validation rules belong to implementation design.

Invalid source records should not necessarily cause an entire collection run to fail.

Individual records may instead be rejected or deferred while valid records continue through the pipeline.

---

## Publication

Publication is an explicit responsibility of the data application.

The data application stores normalized source data in `catalog`, then writes
qualified product snapshots to `publication.event_snapshot`.
`publication.discoverable_events` filters snapshots for current discovery.
The backend reads `publication`, not intermediate `catalog` rows.

Publication stores one snapshot per occurrence (`loc_occurrence_id`). Grouping multiple occurrences into one discovery list card is not a data-application responsibility; the backend derives event-level list views from occurrence snapshots that share the same `loc_event_id`. Organizer pages similarly read `locOrganizerId` from those occurrence snapshots.

The backend does not need to understand:

```text
raw provider schemas
collection mechanisms
external provider reliability
source-specific normalization
source enrichment logic
```

External data collection never occurs as part of a normal user HTTP request.

---

## Updates and Cancellations

Different external sources expose different lifecycle information.

Some may explicitly provide states such as:

```text
cancelled
postponed
rescheduled
```

while others may only change or remove a record.

Source-specific lifecycle interpretation belongs to processing logic.

Repeated collection and idempotent publication allow catalog records to be updated instead of duplicated.

Current publication rules withdraw cancelled, postponed, stale, or absent
occurrences from discovery while retaining snapshots that were published before.
See `data/README.md` for the implemented qualification rules.

---

## AI Tagging

AI-generated tags are a catalog enrichment owned by the data application.

They are generated only after the event has been normalized into a clean Loc representation.

Local tags are assigned during import. An event with a description of at least
80 characters waits for asynchronous Gemini tagging before final publication.
While tagging is pending, the publication decision is held with `AWAITING_TAGS`.
After Gemini succeeds, publication can proceed even if Gemini returned no tags.
After five failed attempts, tagging is marked `GAVE_UP` and publication can
proceed with local tags. Events with shorter or missing descriptions use local
tags without a Gemini request. The published payload always has at least one
tag; `other` is the final fallback. Other publication rules still apply.

Generated tags may describe characteristics such as:

```text
techno
outdoor
family-friendly
live-music
business
beginner-friendly
```

Generated tags are descriptive metadata.

They are not authoritative event facts and must never be used to manufacture factual information such as:

```text
venue
address
city
date/time
price
organizer
```

AI tags are distinct from:

```text
Loc category
source categories
source tags
```

The data application owns writing generated tags back to the event catalog.

The backend reads the final published tags as catalog metadata.

---

## Messaging

RabbitMQ may be used for asynchronous signals and background work.

Messaging is not authoritative pipeline state.

For example:

```text
persistent PostgreSQL state
        +
optional asynchronous signal
```

A completion signal may be emitted when work becomes available for another asynchronous stage.

If a message is lost, PostgreSQL state must still allow the system to determine that work exists.

Queue names, routing keys, exchanges, retry counts, and other message contracts belong to implementation design.

RabbitMQ does not orchestrate every stage of the pipeline.

---

## PostgreSQL Ownership

Loc uses one PostgreSQL database with logical ownership boundaries.

```text
Data application owns
├── collection state
├── processing state
├── external source metadata
└── published event catalog

Backend owns
├── users
├── authentication/session data
├── saved events
├── comments
├── notification state (design pending)
└── other product/user behaviour
```

The backend reads the qualified publication contract. It does not use
intermediate catalog rows as product event data.

The data application must not modify backend-owned user or product state.

Catalog structure shared between both applications is a contract and must be changed deliberately.

---

## Object Storage

Object storage is the durable raw-data store.

PostgreSQL stores metadata and operational state about those payloads.

```text
Object Storage
→ original source payload

PostgreSQL
→ which source produced it
→ where it is stored
→ whether collection succeeded
→ whether it was processed
→ processing outcome
```

Object storage and PostgreSQL therefore serve different responsibilities.

---

## Redis

The current data worker does not require Redis.

It may be used later if the data application develops a concrete need for temporary infrastructure such as:

```text
distributed locks
temporary cache
rate-limit coordination
short-lived worker coordination
```

Redis must not become the source of truth for raw data, pipeline state, or published catalog data.

If no concrete data-side use case appears, Redis remains backend/shared infrastructure only.

---

## Failure Handling

The pipeline should tolerate partial failure.

A failure in one source should not stop unrelated sources.

A bad individual record should not automatically cause an entire valid collection to fail.

The architecture leaves room for:

```text
retry
rejected records
quarantine
dead-letter handling
replay
error reporting
```

Exact retry counts, dead-letter configuration, and operational procedures belong to implementation design.

---

## Replay and Reprocessing

Raw storage allows historical input to be processed again.

Conceptually:

```text
Collection Run
      ↓
Raw payload
      ↓
Processing Version 1
      ↓
processing logic changes
      ↓
Processing Version 2
      ↓
same original payload
```

No additional request to the external provider is required.

This allows normalization, enrichment, mapping, and validation logic to evolve safely.

---

## Backend Relationship

The data application and backend have separate responsibilities.

```text
External world
      ↓
Data application
      ↓
Published Loc catalog
      ↓
Backend
      ↓
Loc users
```

The data application answers:

> What events belong in the Loc catalog and what do we know about them?

The backend answers:

> What can users and administrators do with those events?

Examples of backend-owned behaviour include:

```text
search API
event-granular discovery list (grouped by loc_event_id)
saved events
comments
notifications (design pending)
admin workflows
user accounts
```

---

## Repository Structure

The data application remains inside the existing monorepo.

```text
loc-event-platform/
├── backend/
├── data/
├── frontend/
└── docs/
    └── architecture/
        ├── system-architecture.md
        └── data-architecture.md
```

`data/README.md` should document how developers build, configure, run, and operate the data application.

This document describes architectural responsibilities and boundaries.

---

## Architecture Principles

The data architecture follows these principles:

1. Preserve original source data before transforming it.
2. Separate collection from processing.
3. Keep processing replayable.
4. Use PostgreSQL as authoritative operational and catalog state.
5. Use object storage for immutable raw payloads.
6. Keep provider-specific behaviour behind source adapters.
7. Never invent missing factual event data.
8. Allow optional fields to remain unknown.
9. Make same-source processing idempotent.
10. Make publication explicit.
11. Do not ingest external data during normal user requests.
12. Keep catalog enrichment in the data application.
13. Keep shared catalog contracts explicit between data and backend.
14. Treat messaging and caching infrastructure as supporting mechanisms, not sources of truth.
15. Add complexity only when real requirements justify it.

---

## Deferred Implementation Decisions

These future decisions may evolve independently of this architecture document:

```text
cross-source fuzzy deduplication
raw retention duration per provider
future enrichment strategies
future provider adapters
```

Implemented rules, mappings, tables, and schedules are documented in
`data/README.md` and the data application code.

---

## Next Integration Goal

Data collection and publication are implemented. The next integration step is
the redesigned backend reading qualified publication data:

```text
External sources
      ↓
Collection
      ↓
Raw storage
      ↓
Processing
      ↓
Qualified publication
      ↓
Backend reads `publication`
```

Additional sources can use the same collection, replay, and publication
boundaries without changing the backend's product read contract.
