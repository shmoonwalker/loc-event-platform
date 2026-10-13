# Development scripts

| Script | What it does |
|---|---|
| `dev-up.sh` | Starts PostgreSQL and RabbitMQ (`docker compose up -d db rabbitmq`), the infrastructure the data application needs |
| `dev-down.sh` | Stops the local Docker Compose stack |

The backend also uses Redis for rate limiting. Start it with `docker compose up -d redis`, or
see the [backend guide](../backend/README.md).

Plain `docker compose up` does not start the backend container. To run it in Docker, use the
`backend-preview` profile:

```bash
docker compose --profile backend-preview up -d
```
