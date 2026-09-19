# Loc data application

This directory contains Loc's separate Java/Spring Boot data application. It
will collect external event data, store immutable raw payloads in Cloudflare
R2, and keep operational and catalog state in PostgreSQL.

Processing and publication will be implemented incrementally. The detailed
design is documented in
[`../docs/architecture/data-architecture.md`](../docs/architecture/data-architecture.md).

## Project foundation

The project currently contains the application skeleton and the raw object
store. Collection, persistence models, processing, messaging, and tests will be
added in later work.

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
| `R2_ENABLED`    | Enable R2 storage                      | `false` (dev) / `true` (prod) |
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
replace an existing key. `get(key)` reads the stored bytes. SDK failures propagate
to the caller; there is no automatic local fallback. No request is sent at startup
and no bucket is created by the application. Collection is not wired to storage yet.

The client uses the [Cloudflare S3 configuration](https://developers.cloudflare.com/r2/examples/aws/aws-sdk-java/).
