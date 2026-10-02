# Loc data application

This Java 25 application collects RVO and Ticketmaster events into Cloudflare
R2, imports normalized events into PostgreSQL's `catalog` schema, and publishes
the events that pass the product rules into the `publication` schema.

## Normal startup: process saved events

Final qualification runs after startup ingestion and again on a timer. The `publish` command
evaluates the existing catalogue without source collection or raw replay. Set
`PUBLICATION_ENABLED=false` to disable that scanner.

With database and R2 environment variables loaded, start from `data/`:

```bash
./mvnw -Dmaven.test.skip=true spring-boot:run -Dspring-boot.run.profiles=dev
```

No ingestion arguments are needed. Startup lists saved event files for both
sources, including older collection folders, and imports files that have no
successful-processing checkpoint. R2 listing is paginated. This does not call
the source APIs or upload the raw files again. It performs one pass on startup.
Fresh source collection runs on its daily schedule, or by the `collect` and `run`
commands. The schedulers and queue listeners keep the process alive after the
startup pass.

`catalog.processed_raw_object` records success in the same transaction as the
catalog changes. A failed file remains pending for the next startup, while
other files continue processing. Previously imported files without checkpoints
are processed once again using the existing snapshot ordering checks.

New raw snapshots are still saved during each collection. After mapping, the
application compares a hash of the normalized content with the catalog row.
If unchanged, it skips location, time-slot, image and category synchronization,
but still updates snapshot provenance, freshness and source presence. Older
snapshots are rejected before this comparison. Existing rows without a hash
receive one on their next accepted import. Mapper changes that alter normalized
content produce a different hash; explicit `reprocess` can apply those changes.

Optional commands via `-Dspring-boot.run.arguments="..."`:

| Arguments | Behavior |
| --- | --- |
| `collect <source>` | Collect and save a source snapshot without processing it |
| `process ticketmaster` | Process pending saved Ticketmaster event files only |
| `process rvo` | Process pending saved RVO event files only |
| `process-all` | Process pending saved event files for all sources |
| `run ticketmaster` | Collect fresh Ticketmaster data, then process pending saved files |
| `run rvo` | Collect fresh RVO data, then process pending saved files |
| `run-all` | Collect and process both sources |
| `reprocess <source> <rawObjectKey>...` | Explicitly replay selected files even if already processed |

## From catalog to product

The catalog import (`CatalogImportService`) is deliberately permissive: a normalized event is
stored even when it will not pass final publication. `PublicationService` applies the product
rules afterwards and writes `publication.event_snapshot` and
`publication.change_log`. `publication.discoverable_events` is a view over
currently discoverable snapshots. Only that final step is called publication.

List grouping by logical event is defined in the backend product API, not in publication. See [Backend guide](../backend/README.md).

An occurrence is published when it has:

- a title and a public source URL
- a known start date, start time and timezone, and has not ended
- for an in-person event: valid Netherlands coordinates, a city, and a venue name or address
- fresh source details, and a source that still lists it
- no cancellation or postponement

Online events skip the location checks. A missing description, image or specific category is
only a warning.

Tags come from two places:

- Local tags are written at import from the Loc category and the source's own classification
  (Ticketmaster genres, RVO titles and subjects). They cost no API call. `other` is used only
  when nothing more specific exists.
- Gemini runs once per event with a description of at least 80 characters. Such an event is held
  with `AWAITING_TAGS` until Gemini succeeds, and Gemini is only asked about events whose sole
  blocking reason is `AWAITING_TAGS`. A successful answer, even an empty one, is final: later title
  or description changes do not spend another request. After five failed attempts the event is
  marked `GAVE_UP` and published with its local tags. A shorter or missing description counts as
  no description: local tags only, no Gemini call.

Weather is fetched only for published in-person occurrences, using the published coordinates.
Online events and held occurrences never trigger a weather request. Every request attempt
consumes the configured daily weather budget. A forecast older than four days is not shown.

Collection runs on a schedule: once a source's last completed collection is older than
`COLLECTION_INTERVAL` (default one day), it is collected and processed again. Keep this well under
the publication freshness limit (`PUBLICATION_MAX_DETAIL_AGE`, default 72 hours), otherwise
events are withdrawn as stale. Set `COLLECTION_ENABLED=false` to collect only by command.

Automatic discovery reads only `raw/<source>/<run-id>/events/<id>.json` files,
not list pages or other objects. Fresh Ticketmaster collection requires the
`Ticketmaster` API-key environment variable; processing saved files does not.

## Source presence

A saved collection run records its actual country/date scope, start time and
collected event IDs. Only completed runs can deactivate missing catalog events.
Ticketmaster checks the event country and known start times against that run's
window; dates outside the window and uncertain locations/dates are left alone.
RVO collection covers its full list endpoint.

Presence uses collection start order, independently of mapping success. Older
runs cannot reverse newer decisions, and a newer completed run can reactivate
an event. Publication checks completed runs even when first importing an old
file, so importing historical data does not bypass the presence decision.
Source-level transaction locks serialize publication and reconciliation.

Old R2 folders without completion records can still be imported automatically,
but cannot prove that other events disappeared. Legacy database runs without
a recorded scope cannot deactivate rows either. Failed collections do not
produce absence decisions; any successfully saved files remain processable.

## Weather enrichment

Forecasts are catalog enrichment owned by this application, not something the backend fetches
while a page loads. Each row in `catalog.event_weather` belongs to one time slot, because one
event can run on several dates and every date needs its own forecast.

Publication never writes weather. An event is published immediately, with or without a
forecast, and the forecast arrives later.

A scheduled scan (`WeatherRefreshScan`) selects slots whose check is due, claims each one and
puts a message on `loc.weather.refresh` carrying only the slot id. `WeatherRefreshListener`
re-reads the slot, calls Open-Meteo and stores the result. The queue carries the work while
PostgreSQL records which work is due, so a lost message costs nothing — the slot is still due
and the next scan queues it again.

A slot is only eligible when its occurrence is currently published, is in person, and starts in
the future. The coordinates come from the published snapshot. Online events are published
normally and simply never get a forecast.

`next_check_at` decides everything about volume. It is set from the start time, so a slot a
month out is never read at all until it enters the window:

| Time until start | Next check |
| --- | --- |
| More than 14 days | When the slot reaches 14 days out |
| 14 to 7 days | In 3 days |
| 7 to 3 days | In 2 days |
| Under 3 days | Tomorrow |
| Already started | Never again |

A forecast is refreshed at most once a day. Open-Meteo's hourly data reaches 16 days counting
today and its limit is a calendar date, so eligibility stops at 14 days; that margin keeps a
slot at the edge from being due by our clock and rejected by the service.

Four results are handled differently. Values are stored and rescheduled by the table above. An
accepted hour with no values yet is not a failure, so it is retried the next day. A rejection
for being outside the forecast range waits until the slot enters the window. A timeout or
server error is a real failure: the queue retries it with backoff, a repeated failure reaches
`loc.weather.refresh.dlq`, and the stored backoff takes over from there.

Writes are guarded. The stored coordinates and hour are compared on every update, so a
forecast that arrives after its slot moved is discarded rather than saved against the wrong
date. Catalog import also deletes weather rows when a slot's start hour or an event's coordinates
change, and the scan clears anything it missed.

Temperature, wind, rain chance and the raw WMO code are stored as numbers. Turning a code into
words is left to whoever reads the catalog.

### Running with weather enabled

With `WEATHER_ENABLED=true` (the default) the application no longer exits after its startup
import: the scheduler and the queue listener keep it running as a worker. It needs RabbitMQ,
which `docker-compose.yml` now provides:

```bash
docker compose up -d rabbitmq
```

| Variable               | Purpose                                             | Default    |
| ---------------------- | --------------------------------------------------- | ---------- |
| `WEATHER_ENABLED`      | Run the scan and the queue listener                  | `true`     |
| `WEATHER_SCAN_INTERVAL`| Delay between scans, ISO-8601                        | `PT5M`     |
| `WEATHER_SCAN_LIMIT`   | Most slots queued per scan                           | `200`      |
| `WEATHER_DAILY_LIMIT`  | Maximum Open-Meteo request attempts per UTC day     | `500`      |
| `RABBITMQ_HOST`        | Broker host                                          | `localhost`|
| `RABBITMQ_PORT`        | Broker port                                          | `5672`     |
| `RABBITMQ_USER`        | Broker user                                          | `guest`    |
| `RABBITMQ_PASSWORD`    | Broker password                                      | `guest`    |

Set `WEATHER_ENABLED=false` to disable weather scans and their queue listener.
Tagging has its own `TAGGING_ENABLED` setting and may still require RabbitMQ;
collection and publication schedules are controlled separately.

The scan limit and the listener concurrency together bound the request rate. Open-Meteo needs
no API key but is fair-use, so a backlog is worked through over several scans instead of being
sent at once.

## Raw object storage

Raw payloads are written through `nl.loc.data.storage.RawObjectStore`. The R2 implementation is enabled by
`loc.object-storage.enabled`:

| `enabled` | Implementation                  | Where payloads go                              |
| --------- | ------------------------------- | ---------------------------------------------- |
| `false`   | None | Storage is disabled |
| `true`    | `S3RawObjectStore`              | Cloudflare R2 bucket via the S3 API             |

Object keys follow `raw/<source>/<collection-run>/<file>`.

### Environment variables

| Variable        | Purpose                                                     | Default      |
| --------------- | ----------------------------------------------------------- | ------------ |
| `R2_ENABLED`    | Enable R2 storage                      | `true` |
| `R2_ENDPOINT`   | `https://<account-id>.r2.cloudflarestorage.com`             | —            |
| `R2_REGION`     | R2 accepts `auto`                                           | `auto`       |
| `R2_ACCESS_KEY` | R2 API token access key id                                  | —            |
| `R2_SECRET_KEY` | R2 API token secret access key                              | —            |
| `R2_BUCKET`     | Bucket name (separate buckets for dev and prod)             | —            |

When `R2_ENABLED=true` the application fails at startup with a list of any
missing `R2_*` settings.

### Configure Cloudflare R2

Use an existing private bucket and R2 S3 credentials with Object Read & Write
permission for that bucket. Set these environment variables (or load them from
`data/.env` before starting the application):

```dotenv
R2_ENABLED=true
R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
R2_REGION=auto
R2_ACCESS_KEY=<r2-access-key-id>
R2_SECRET_KEY=<r2-secret-access-key>
R2_BUCKET=<bucket-name>
```

Copy the S3 endpoint from your bucket settings, without the bucket path; use the
jurisdiction-specific endpoint if your bucket has one. Spring Boot does not
load `.env` automatically. Keep actual credentials out of committed files.

`RawObjectStore.put(key, payload, contentType)` uploads bytes and refuses to
replace an existing key. `get(key)` reads the stored payload and metadata;
`list(prefix)` discovers saved objects. SDK failures propagate to the caller;
there is no local fallback and the application does not create buckets.

The client uses the [Cloudflare S3 configuration](https://developers.cloudflare.com/r2/examples/aws/aws-sdk-java/).
