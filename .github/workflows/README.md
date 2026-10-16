# GitHub workflows

| Workflow | Runs on | Steps |
|---|---|---|
| `deploy.yaml` | Pushes to `main` (except changes to Markdown, `docs/` and `screenshots/` only) and manual runs | Runs the four workflows below, pushes the images to GHCR, deploys them to production |
| `backend-ci-cd.yaml` | Pull requests that change `backend/`; called by `deploy.yaml` | Checkstyle, tests, Docker image build (amd64 and arm64) |
| `frontend-ci-cd.yaml` | Pull requests that change `frontend/`; called by `deploy.yaml` | Lint, unit tests, Docker image build (amd64 and arm64) |
| `data-ci-cd.yaml` | Pull requests that change `data/`; called by `deploy.yaml` | Docker image build (amd64 and arm64), which also compiles the code |
| `compose-prod.yaml` | Pull requests that change `docker-compose.prod.yml`, `.env.production.example` or `deploy/`; called by `deploy.yaml` | `docker compose config` with the template filled with dummy values, ShellCheck on `deploy/deploy.sh` |

On pull requests the images are only built. On `main` they are pushed as
`ghcr.io/shmoonwalker/loc-<app>:<commit sha>` and deployed; see [deploy/README.md](../../deploy/README.md).
