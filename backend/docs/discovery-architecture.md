# Discovery backend architecture and repository guide

Read [the API contract](discovery-api.md) for product behavior, examples, and frontend
integration. This guide explains the implementation and the decisions behind it.

## Ownership and data flow

The data application collects, normalizes, qualifies, and publishes event data.
The backend reads qualified publication snapshots and shapes the product API.
It does not query raw provider files or modify catalog/publication records.

`PublicationService` in the data application builds each occurrence's JSON document
and upserts it into `publication.event_snapshot.payload`, a PostgreSQL JSONB column.
The table also has typed event/occurrence IDs, revision, discoverability, state,
and publication timestamps. A snapshot is the current published representation
per occurrence; the separate change log retains publication changes.

`publication.discoverable_events` is an ordinary SQL view over those snapshots,
requiring `discoverable=true` and `payload.validUntil > CURRENT_TIMESTAMP`. The
repository adds the user's event/date/time/location/text filters. Eligibility and
request matching are separate decisions.

JSONB is a database storage type, **not a file format read by this backend**.
It allows publication to expose a self-contained nested contract without requiring
backend joins across the data application's internal catalog tables. It is not a
Spring Boot requirement, and not necessarily the fastest model for all search.

## Structure

```text
config/
  SecurityConfig                 anonymous endpoint allowlist; default deny
  GlobalExceptionHandler         request errors as ProblemDetail
  TimeConfig                     system UTC Clock bean

event/
  controller/
    PublicBrowseController       bind HTTP input, invoke parsing/services
    BrowseCriteriaParser         normalize and validate query strings
    InvalidBrowseQueryException   boundary validation failure
  service/
    EventBrowseService           time ranges, sort policy, city suggestions
    HomeService                  sections, homepage-only fallback, browse URLs
    FilterOptionsService         complete static vocabulary from supported catalogs
  repository/
    PublicEventRepository        SQL, parameter binding, grouping and pagination
    EventCardRowMapper           ResultSet to public preview DTO
  model/
    BrowseCriteria               typed search criteria
    BrowseLimits                 shared query/page-size limits
    TimeWindow                   pure Amsterdam calendar calculations
    When, Place, EventSort       typed choices
    RailMode                     meaning of a homepage section
  dto/request/                   HTTP query binding objects
  dto/response/                  explicit public response records

category/model/EventCategory     public slug / catalog name mapping
tag/model/EventTag               supported tag slugs and display labels
city/model/                      city representation and fallback display naming
```

Feature packages keep related behavior together. Layers have distinct reasons to
change. No empty `search` package, generic base repository, service interface per
class, or frontend code is needed. Repository is the name used consistently for
persistence reads, even though these operations do not create/update/delete rows.

## Request flow

1. Spring binds URL parameters into `EventsQuery`.
2. `BrowseCriteriaParser` validates and normalizes them into `BrowseCriteria`.
3. `EventBrowseService` reads the injected clock and computes the concrete range.
4. `PublicEventRepository.search` receives criteria, range, and effective sort.
5. PostgreSQL filters occurrence rows, then chooses one representative per event.
6. `EventCardRowMapper` maps the selected columns to cards.
7. `EventPage` supplies totals and pagination; Spring serializes the DTO.

Parsing belongs at the HTTP boundary. Time-window and fallback decisions belong in
services. SQL, driver-specific parameters, and ResultSet mapping belong in the
repository package. The repository returns an explicit read DTO; an extra entity
with identical fields would not add value for this read-only projection.

## Spring dependencies

`PublicEventRepository` is a concrete `@Repository` class. Constructor injection
supplies `JdbcClient` and `EventCardRowMapper`. Spring Boot configures JDBC from the
datasource configuration and PostgreSQL driver. There is one search implementation,
so there is no interface or engine property; add one only when a second engine exists.

`EventBrowseService` depends on the repository and Clock. `HomeService` depends on
the repository for city resolution, on EventBrowseService for searches, and on
Clock. It uses one instant for a complete homepage calculation. FilterOptionsService
uses category/tag catalogs and does not query publication.

This is a repository layer without Spring Data JPA. JdbcClient suits explicit
PostgreSQL operations such as JSONB filtering, full-text search, and DISTINCT ON.
JPA would not remove those query requirements.

## Reading the repository

### Reusable SQL expressions

- `STARTS`: extract `payload.schedule.starts_at` as text, cast to timestamptz.
- `ENDS`: published end timestamp, falling back to start when absent.
- `CITY_SLUG`: trim/lowercase the city and replace punctuation runs with hyphens.
- `DOCUMENT`: combine title, description, venue, city, categories, and tags into
  a PostgreSQL tsvector using the simple search configuration.

`->` extracts JSON and `->>` extracts text. `::timestamptz` converts timestamp text
for comparisons. Invalid published field types can cause SQL errors: the producer
must honor the publication contract. JSONB validates JSON syntax, not your full
business schema, and Java compilation cannot validate string paths in SQL.

### Search, count, and cards

`search` runs `count`, skips the card query if no matches exist, and builds EventPage.
`count` counts distinct logical events after filters. `cards` selects the earliest
matching occurrence for each event, maps its preview fields, sorts and paginates.

`DISTINCT ON (loc_event_id)` with order by event ID, start, and occurrence ID chooses
a deterministic representative. Filters run first, so a weekend card uses the
matching weekend occurrence even if the same event has another earlier date.
The outer order uses start/event ID or relevance/start/event ID. `LIMIT` and a long
`page * size` offset operate on the grouped logical events.

The SQL selects only card fields. Categories and tags remain in SQL filtering and
full-text search, but are not returned on cards. The previous handwritten JSON
parser was removed during the earlier cleanup; after cards were simplified, the
row mapper no longer needs Jackson either. There is no replacement custom parser.

### Filters and parameter binding

Both count and cards use the same occurrenceFilter builder. It checks start/end
bounds and adds city, category OR (`jsonb_exists_any`), tag AND (`jsonb_exists_all`),
text matching, place, and local start-time conditions only when requested.

Time-of-day filtering uses `(start AT TIME ZONE :zone)::time`. Normal windows use
`>= from AND < to`; overnight windows use `>= from OR < to`. Custom dates refer to
the occurrence's local start date; they do not extend overnight into an extra date.

`bind` converts category slugs to catalog names, enums to publication values,
collections to SQL arrays, and Instant to UTC OffsetDateTime. It supplies named
parameters, including explicit types for nullable timestamp/query values.
User strings are never concatenated into SQL. String formatting inserts only
code-owned query fragments; supported sort fragments are selected by enum.

### City reads

`findCity` finds a published display name for a slug, preferring the most frequent
name with an alphabetical tie-break. It uses publication eligibility but does not
apply the browse time range. HomeService derives a display label if nothing matches.

`suggestCities` uses upcoming start criteria and groups/counts distinct events by
city name and slug. It matches name or slug with ILIKE, puts Amsterdam first for
an empty query, then orders by count and name. It is not a geographic directory;
canonical city aliases and radius filtering are future work.

### Mapping

EventCardRowMapper reads SQL columns, converts timestamps to Instant and place to
a typed enum, preserves null city/venue/image, and constructs EventCard. It exposes
neither the raw payload nor internal occurrence IDs. Online status is explicit.

## Fallback and navigation design

HomeService owns fallback because it is product discovery policy. If Tonight's
count is zero, it requests upcoming physical events and changes the mode to
STARTING_SOON. It does not modify the general repository search behavior.

Each section includes an exact browse URL and a broader URL. Both start at page
zero. The exact link is generated from the same city, preset, and place used to
retrieve the preview. A broadened URL explicitly drops city/preset restrictions;
it must be labelled accordingly in the UI. Near you and Weekend remain truthful
when empty. The root browse link provides the normal unfiltered collection.

The old unbounded ConcurrentHashMap cache is gone. Home does not retain arbitrary
user-supplied city keys, and later requests see publication changes without a local
TTL delay. Add bounded caching only after measuring real traffic and freshness needs.

## Time and transactions

Production injects Clock.systemUTC(); tests inject a fixed Clock. TimeWindow is a
pure utility applying Europe/Amsterdam to calendar boundaries. Database publication
expiry still uses PostgreSQL time, independently of the Java clock.

Repository reads use `@Transactional(readOnly=true)`. This is transaction intent,
not a substitute for read-only database permissions or a promise of repeatable-read
isolation. Count/cards may observe concurrent changes at default Read Committed.
A homepage's several repository calls are not one database snapshot. If strict
consistency is required later, introduce an explicit isolation/query strategy.

## Performance and future boundaries

The new homepage removes the two facet queries. A populated normal homepage makes
up to nine SQL reads (city plus count/cards for four sections). Empty Tonight adds
its count before the fallback, for up to ten reads. Empty rails skip card retrieval.
No production performance claim follows from the correctness tests.

JSON paths, timestamp casts, city normalization, and search vectors are calculated
in queries. Inspect representative EXPLAIN ANALYZE plans before optimizing. Typed
publication/search columns and matching indexes may eventually simplify the read
path; a generic JSONB index does not automatically optimize every expression here.

The backend still needs separate future work for event detail, accounts, saved
events, comments, product moderation, canonical city aliases, geographic radius,
and possibly cursor pagination. No new endpoints for those are implied by this API.

## Verification

From backend, run `./mvnw -o clean test checkstyle:check` when dependencies are cached;
omit `-o` for the first dependency download. Docker must be accessible for the real
PostgreSQL Testcontainers suite. Tests seed an isolated publication contract, not
the developer's live database. No catalog/publication migrations are changed.

See the regression coverage section of discovery-api.md for scenarios. Keep docs,
OpenAPI, DTOs, filter vocabulary, and tests aligned when changing the contract.
