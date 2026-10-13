# GitHub workflows

| Workflow | Runs on | Steps |
|---|---|---|
| `backend-ci-cd.yaml` | Pushes to `main` and pull requests that change `backend/` | Checkstyle, tests, Docker image build |
| `frontend-ci-cd.yaml` | Pushes to `main` and pull requests that change `frontend/` | Lint, Docker image build |

Images are built to check that they build; they are not pushed. Production deploys run on
Railway.
