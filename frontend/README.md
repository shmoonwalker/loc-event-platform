# Frontend

This Angular application is Loc's public home page: highlight rails (tonight, this
weekend, near you, online) and a searchable, filterable event list. Filters live in the
URL query string, so every filtered view is a shareable link. The previous holding page
is still available at `/holding`. Event detail pages do not exist yet, so event links
currently land on the not-found page.

Planned discovery home: one card per `locEventId`, showing the next upcoming date and optionally a count of further dates (for example "+ N more dates"). The event detail route loads by event id and renders all occurrences returned by the backend. Weather on the list is omitted by default; per-date weather appears on detail. If the list ever shows weather, it may only reflect the next occurrence when that date is inside the forecast window. Full rules are in the [backend guide](../backend/README.md).

From `frontend/`, install dependencies with `npm ci`. Use `npm start` for the
development server at `http://localhost:4200/` (it proxies `/api` to the backend on
`localhost:8081`, see `proxy.conf.json`), `npm run build` to build, `npm test` to run
the unit tests and `npm run lint` to lint. There is no end-to-end test runner configured.

Styling uses Tailwind CSS v4. The design tokens (light and dark) are CSS variables in
`src/styles.css`.

### Deployment

The production image serves the built app with nginx and forwards `/api/` to the backend.
Set the `BACKEND_URL` environment variable on the frontend service to the backend's
address, for example `http://<backend-service>.railway.internal:8080`. It defaults to
`http://backend:8080` (the Docker Compose service name). Without a reachable backend every
API call answers `502`. See `nginx.conf.template` and `Dockerfile`.

The intended product boundary is described in the
[system architecture](../docs/architecture/system-architecture.md): the
frontend will communicate with the backend API, not directly with PostgreSQL
or the data worker.
