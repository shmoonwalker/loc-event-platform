# Loc data application

This Java 25 application collects RVO and Ticketmaster events into Cloudflare
R2 and publishes normalized events into PostgreSQL's `catalog` schema.

## Normal startup: process saved events

With database and R2 environment variables loaded, start from `data/`:

```bash
./mvnw -Dmaven.test.skip=true spring-boot:run -Dspring-boot.run.profiles=dev
```

No ingestion arguments are needed. Startup lists saved event files for both
sources, including older collection folders, and imports files that have no
successful-processing checkpoint. R2 listing is paginated. This does not call
the source APIs or upload the raw files again. It performs one pass on startup;
it is not a continuously polling worker.

`catalog.processed_raw_object` records success in the same transaction as the
catalog changes. A failed file remains pending for the next startup, while
other files continue processing. Previously imported files without checkpoints
are processed once again using the existing snapshot ordering checks.

New raw snapshots are still saved during each collection. After mapping, the
application compares a hash of the normalized content with the catalog row.
If unchanged, it skips location, time-slot, image and price synchronization,
but still updates snapshot provenance, freshness and source presence. Older
snapshots are rejected before this comparison. Existing rows without a hash
receive one on their next accepted import. Mapper changes that alter normalized
content produce a different hash; explicit `reprocess` can apply those changes.

Optional commands via `-Dspring-boot.run.arguments="..."`:

| Arguments | Behavior |
| --- | --- |
| `process ticketmaster` | Process pending saved Ticketmaster event files only |
| `process rvo` | Process pending saved RVO event files only |
| `run ticketmaster` | Collect fresh Ticketmaster data, then process pending saved files |
| `run rvo` | Collect fresh RVO data, then process pending saved files |
| `run-all` | Collect and process both sources |
| `reprocess <source> <rawObjectKey>...` | Explicitly replay selected files even if already processed |

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
