# Development scripts

The data worker and backend are at different stages of the redesign.

- `dev-up.sh` starts PostgreSQL and RabbitMQ for the data worker. It does not
  start the backend shell, which has no product endpoints or database access yet.
- `dev-down.sh` stops the local Compose stack.

Plain `docker compose up` also leaves the backend shell out. To preview it,
use the explicit `backend-preview` Compose profile. The shell has no old
Flyway migrations and cannot recreate the removed schemas.

For the current data worker, follow [`data/README.md`](../data/README.md).
The current PostgreSQL data model uses `catalog` for normalized data and
`publication` for qualified product snapshots.
