# GitHub workflows

| Workflow | Runs on | Steps |
|---|---|---|
| `backend-ci-cd.yaml` | Pushes to `main` and pull requests that change `backend/` | Checkstyle, tests, Docker image build |
| `frontend-ci-cd.yaml` | Pushes to `main` and pull requests that change `frontend/` | Lint, Docker image build |
| `data-ci-cd.yaml` | Pushes to `main` and pull requests that change `data/` | Docker image build (amd64 and arm64), which also compiles the code |
| `compose-prod.yaml` | Pushes to `main` and pull requests that change `docker-compose.prod.yml` or `.env.production.example` | `docker compose config` with the template filled with dummy values |

Images are built to check that they build; they are not pushed. Nothing deploys automatically.
