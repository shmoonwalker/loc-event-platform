# Frontend

This Angular application is Loc's user interface. It has these pages:

| Route | Page |
|---|---|
| `/` | Home: highlight rails (tonight, this weekend, near you, online) and a searchable, filterable event list |
| `/events/:id` | Event detail: description, location, organizers, every upcoming date, per-date weather and comments |
| `/organizers/:id` | Organizer profile and their upcoming events |
| `/register` | Create an account with name, email and password, then "check your email" |
| `/verify-email` | Opened from the verification or email-change email; verifies the account (or its new address) or offers a new link |
| `/login` | Log in with email and password, or resend verification for an unverified account |
| `/forgot-password` | Ask for a password reset link; always shows the same "check your email", whether or not the email has an account |
| `/reset-password` | Opened from the reset email; choose a new password, which signs out every session, or request a new link when this one is invalid or expired |
| `/me` | Signed in: the dashboard with saved events and followed organizers |
| `/me/settings/profile` | Signed in: change the display name, pick a preset avatar, or upload a picture (cropped and zoomed in a round preview first) and remove it again |
| `/me/settings/security` | Signed in: change the email (the current one is shown) or the password |
| `/me/settings/account` | Signed in: delete the account |
| `/holding` | Holding page from the earlier version of the site |

The list's filters are picked from menus: date (a preset or your own date range), time
of day (morning, afternoon, evening or late night), one city, and any number of
categories and tags. Filters live in the URL query string, so every filtered view is a
shareable link. The list shows one card per event; the event page lists all of its
dates. The frontend talks only to the backend API, described in the [Discovery API](../backend/docs/discovery.md).

From `frontend/`, install dependencies with `npm ci`. Use `npm start` for the
development server at `http://localhost:4200/` (it proxies `/api` to the backend on
`localhost:8081`, see `proxy.conf.json`), `npm run build` to build, `npm test` to run
the unit tests and `npm run lint` to lint. There is no end-to-end test runner configured.

Styling uses Tailwind CSS v4. The design tokens (light and dark) are CSS variables in
`src/styles.css`.

### Current user and roles

`AuthSession.start(accessToken)` loads `/api/auth/me`; sign-in callers subscribe before
opening the app. `refresh()` uses the same path on startup and after an expired token.
The header reads `currentUser` for the avatar, display name and role, and logout clears
both the token and account. Neither is persisted in browser storage. The profile settings
hand each account the profile API returns to `AuthSession.update()`, so the header
shows a new name or avatar without a reload. Preset avatars are served from
`public/avatars/`; uploaded pictures come from the image bucket's own domain.

Use `isAdmin()` for admin-only actions and `adminGuard` for admin routes. The backend
still authorizes every request. Visitors see sign-up and login in the header; members
and admins see only their avatar. It opens a small menu with their name and role,
Account settings, Dashboard (`/me`) and Log out; Escape or a click outside closes it.
Logging out keeps the visitor on a public page and opens the home page from a page
that needs an account (`needsAccount()` checks the route's guards). Google sign-in
must use this same session path on return.

### Admin panel

`/admin` is the admin panel, open only to admins (`adminGuard`); admins reach it from
the Admin link in the avatar menu, which members do not see. It has its own layout
instead of the member header and footer: a plum sidebar with an Admin badge, the
section links and Back to site, and a top bar with the theme switch. On small screens
the sidebar becomes a top bar whose Sections button opens the links. Its colours are
the `--admin-*` tokens in `src/styles.css`, and its buttons and fields use the
`admin-*` classes there. The start page is an overview with a card per section and the
number of items waiting there; sections are listed in `features/admin/admin-sections.ts`.
An unknown page under `/admin` opens the overview.

- **Reports** (`/admin/reports`) lists open reports, one row per event or comment,
  newest activity first, with the number of reports and their reasons. Event rows link
  to the event by title. Dismiss closes all of an item's open reports without changing
  it. Hide event asks for a reason, takes the event out of every public list and page,
  and closes its reports.
- **Hidden events** (`/admin/hidden-events`) lists hidden events, most recent first, with
  who hid each one, when and why, and Unhide. The public event API does not return a
  hidden event, so rows show its id instead of its title.

The endpoints are in the [admin moderation API](../backend/docs/admin-moderation.md).

### Comments

The event page lists comments newest first, each with its replies, one page at a time.
Anyone can read them. Signed-in members write comments, reply to top-level comments,
and edit or delete their own; deleting asks to confirm first. Visitors get a link to log
in instead of the form. A deleted comment that still has replies shows as "Comment
deleted", authors of deleted accounts show as the API names them, and official replies
carry an "Official" badge. API errors, such as text that is too long or posting too
often, show under the form. Members can report the event (next to the save button) or
someone else's comment: a dialog asks for one of the fixed reasons and optional text,
then says thanks. Errors, such as reporting too often, show in the dialog. Visitors and
admins do not see Report. An admin sees Official reply instead of Reply, which posts a
reply with the "Official" badge, and Remove on every comment and reply instead of
Delete. Remove asks for a reason, which only admins see, and takes the comment away
for everyone; the admin endpoints are in [Admin comments](../backend/docs/admin-comments.md). The endpoints are in the [Member API](../backend/docs/member.md).

### Account settings

`/me/settings` is one page with the sections Profile, Sign-in & security and Account
listed on the left (above the section on small screens); each section has its own URL.
`/me/profile` and `/me/account` redirect to it. The email form shows the current address
and refuses it as the new one. A picked avatar picture is drawn on a canvas inside a
circle: drag it (or use the arrow keys) and zoom to choose the part to keep. Only that
square, 512 × 512 pixels, is uploaded, in the picked file's format. Clicking the avatar
opens a larger view in a dialog.

Changing the password, changing the email and deleting the account each ask for the
current password. A wrong one is shown under that field; the interceptor does not
refresh the session for it, because the API answers it with `401` like an expired token.
Deleting the account ends the session in the UI and opens the home page.

### Saved events

Unsaving an event on the dashboard removes its card at once and says "Removed"; the
count goes down with it. An event that is no longer available still opens from the saved
list; its page says "This event is no longer available." and shows no weather.

### Deployment

The production image serves the built app with nginx and forwards `/api/` to the backend.
Set the `BACKEND_URL` environment variable on the frontend service to the backend's
address. It defaults to `http://backend:8080`, the backend's alias in `docker-compose.prod.yml`. Without a reachable backend every
API call answers `502`. See `nginx.conf.template` and `Dockerfile`.

The frontend never talks to PostgreSQL or the data application directly; see the
[system architecture](../docs/architecture/system-architecture.md).
