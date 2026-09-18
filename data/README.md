# Loc data application

This directory contains Loc's separate Java/Spring Boot data application. It
will collect external event data, store immutable raw payloads in Cloudflare
R2, and keep operational and catalog state in PostgreSQL.

Processing and publication will be implemented incrementally. The detailed
design is documented in
[`../docs/architecture/data-architecture.md`](../docs/architecture/data-architecture.md).

## Project foundation

The project is currently only scaffolded. Collection, persistence models,
processing, messaging, and tests will be added in later work.

R2 is disabled by default. Enable it only with environment variables such as
`R2_ENABLED`, `R2_ENDPOINT`, `R2_ACCESS_KEY`, `R2_SECRET_KEY`, and `R2_BUCKET`.
