# Frontend

This Angular application is Loc's user interface. It has these pages:

| Route | Page |
|---|---|
| `/` | Home: highlight rails (tonight, this weekend, near you, online) and a searchable, filterable event list |
| `/events/:id` | Event detail: description, location, organizers, every upcoming date and per-date weather |
| `/organizers/:id` | Organizer profile and their upcoming events |
| `/holding` | Holding page from the earlier version of the site |

Filters live in the URL query string, so every filtered view is a shareable link. The
list shows one card per event; the event page lists all of its dates. The frontend talks
only to the backend API, described in the [Discovery API](../backend/docs/discovery.md).

From `frontend/`, install dependencies with `npm ci`. Use `npm start` for the
development server at `http://localhost:4200/` (it proxies `/api` to the backend on
`localhost:8081`, see `proxy.conf.json`), `npm run build` to build, `npm test` to run
the unit tests and `npm run lint` to lint. There is no end-to-end test runner configured.

Styling uses Tailwind CSS v4. The design tokens (light and dark) are CSS variables in
`src/styles.css`.

### Deployment

The production image serves the built app with nginx and forwards `/api/` to the backend.
Set the `BACKEND_URL` environment variable on the frontend service to the backend's
address. It defaults to `http://backend:8080`, the backend's alias in `docker-compose.prod.yml`. Without a reachable backend every
API call answers `502`. See `nginx.conf.template` and `Dockerfile`.

The frontend never talks to PostgreSQL or the data application directly; see the
[system architecture](../docs/architecture/system-architecture.md).
