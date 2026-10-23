# Frontend

This Angular application is Loc's user interface. It has these pages:

| Route | Page |
|---|---|
| `/` | Home: highlight rails (tonight, this weekend, near you, online) and a searchable, filterable event list |
| `/events/:id` | Event detail: description, location, organizers, every upcoming date and per-date weather |
| `/organizers/:id` | Organizer profile and their upcoming events |
| `/register` | Create an account with name, email and password, then "check your email" |
| `/verify-email` | Opened from the verification email; verifies the account or offers a new link |
| `/login` | Log in with email and password, or resend verification for an unverified account |
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

### Current user and roles

`AuthSession.start(accessToken)` loads `/api/auth/me`; sign-in callers subscribe before
opening the app. `refresh()` uses the same path on startup and after an expired token.
The header reads `currentUser` for the display name and role, and logout clears both
the token and account. Neither is persisted in browser storage.

Use `isAdmin()` for admin-only actions and `adminGuard` for admin routes. The backend
still authorizes every request. Account and admin navigation will be added with their
pages (#104, #108, #113); the header currently offers logout to members and admins,
and login/registration to visitors. Google sign-in (#74) is separate work and must use
this same session path on return.

### Deployment

The production image serves the built app with nginx and forwards `/api/` to the backend.
Set the `BACKEND_URL` environment variable on the frontend service to the backend's
address. It defaults to `http://backend:8080`, the backend's alias in `docker-compose.prod.yml`. Without a reachable backend every
API call answers `502`. See `nginx.conf.template` and `Dockerfile`.

The frontend never talks to PostgreSQL or the data application directly; see the
[system architecture](../docs/architecture/system-architecture.md).
