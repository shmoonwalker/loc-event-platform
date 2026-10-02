# Frontend

This Angular application currently displays a temporary holding page while
Loc's backend and product experience are redesigned. It has no event API
integration or application routes yet.

Planned discovery home: one card per `locEventId`, showing the next upcoming date and optionally a count of further dates (for example "+ N more dates"). The event detail route loads by event id and renders all occurrences returned by the backend. Weather on the list is omitted by default; per-date weather appears on detail. If the list ever shows weather, it may only reflect the next occurrence when that date is inside the forecast window. Full rules are in the [backend guide](../backend/README.md).

From `frontend/`, install dependencies with `npm ci`. Use `npm start` for the
development server at `http://localhost:4200/`, `npm run build` to build, and
`npm test` to run the existing unit test. There is no end-to-end test runner
configured.

The intended product boundary is described in the
[system architecture](../docs/architecture/system-architecture.md): the
frontend will communicate with the backend API, not directly with PostgreSQL
or the data worker.
