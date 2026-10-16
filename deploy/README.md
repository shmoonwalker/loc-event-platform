# Production deployment

Every push to `main` that changes more than Markdown, `docs/` or `screenshots/` deploys to
https://loc.shadibuilds.dev. **Actions → Deploy → Run workflow** on `main` deploys the current `main` again.

## What a deployment does

1. **Checks.** Backend Checkstyle and tests, frontend lint and unit tests, the data build, and a
   `docker compose config` check of `docker-compose.prod.yml`. If any fails, nothing is pushed or deployed.
2. **Images.** GitHub-hosted runners build `linux/amd64` and `linux/arm64` images and push them to
   `ghcr.io/shmoonwalker/loc-{backend,data,frontend}:<commit sha>`. There is no `latest` tag; a release is a commit SHA.
3. **Upload.** Over SSH (key authentication, verified `known_hosts`), the job copies `docker-compose.prod.yml`
   and `deploy.sh` to `<DEPLOY_PATH>/releases/<sha>/`. The server's configuration therefore always matches the
   release, and never depends on a Git checkout on the server.
4. **Pull.** The server pulls the three images with the job's GitHub token (read-only, expires with the job).
   A pull failure leaves the running release untouched.
5. **Roll out.** `deploy.sh` replaces one container at a time, stopping the old one before starting the new one:
   - `loc-data` first, because its Flyway migrations own the `catalog` and `publication` schemas the backend reads.
     It must log Spring Boot's `Started … in … seconds` (migrations done) without stopping or restarting.
   - `loc-backend` (runs its own Flyway migrations on `app`), until its health check is `healthy`.
   - `loc-frontend`, until its health check is `healthy`.
   - `GET http://127.0.0.1:3001/` and `/api/categories` must succeed: frontend, proxy, backend and database.
   - `loc-data` must still be running with zero restarts.
6. **Public check.** The job requests `https://loc.shadibuilds.dev/api/categories`. Because Cloudflare can
   challenge GitHub's runners, a failure here is a warning, not a failed deployment.

A container that stops or restarts during its wait fails the deployment immediately, so a restart loop
never counts as a slow start.

The rollout touches only `loc-data`, `loc-backend` and `loc-frontend` (`--no-deps`). It never runs
`docker compose down`, never prunes, and does not restart PostgreSQL, RabbitMQ, Redis, other applications
or Cloudflare Tunnel. LOC's containers have no volumes; `.env.production` stays on the server and is never
printed (the script never runs `docker compose config` without `--images`).

**Downtime.** Each service is stopped before its replacement starts, so this is not zero-downtime:
`/api` answers `502` for the minute or so the backend needs to start, and the site for a few seconds while
nginx restarts. The data worker shuts down gracefully (40 s), and its checkpoints and claims are in
PostgreSQL. The new worker resumes from them; its scheduled jobs start after their initial delays.

**Serialization.** Deploy runs share the concurrency group `production`. A running deployment is never
cancelled. A newer run waits, and replaces any older run that is still waiting. On the server the rollout
holds `<DEPLOY_PATH>/deploy.lock` and runs under `nohup`, so a cancelled job or dropped connection does not
stop it midway.

## Status

- GitHub: **Actions → Deploy** for each run and its log; **Environments → production** for the deployed commit.
- Server (as the deploy user):

  ```bash
  cat /opt/loc/deploy-history                     # <time> <sha> ok|failed
  readlink /opt/loc/current                       # last successful release
  cat /opt/loc/releases/<sha>/deploy.log          # full rollout log of one release
  docker ps --filter label=com.docker.compose.project=loc-prod
  docker logs --tail 200 loc-backend              # or loc-data, loc-frontend
  ```

## When a deployment fails

- **Checks, build, upload or pull failed:** production still runs the previous release. Fix it and push again.
- **Roll out failed:** the log ends with the state of the LOC containers and their last 100 log lines.
  Services before the failing one run the new release; the failing one and those after it run what
  they ran before, unless the failing one is restarting. Fix forward (push a fix to `main`), or roll back.

### Rollback

Every release's images and files stay on the server, so a rollback needs neither GitHub nor a rebuild:

```bash
ssh deploy@62.238.119.8 /opt/loc/releases/<previous sha>/deploy.sh
```

Take `<previous sha>` from the last `ok` line of `deploy-history`; a failed rollout also prints it. It runs the
same checks with that release's own Compose file. Re-running an older Deploy run in GitHub does the same, but it
also rebuilds.

**Database migrations are not rolled back.** Flyway ignores migrations newer than the ones the older release
knows, so the older release starts, but only works if the newer migrations were backward compatible (added
tables or nullable columns). If a newer migration dropped or renamed something the older code uses, the backend
fails Hibernate schema validation at start-up, or queries fail. In that case fix forward instead of rolling back.
Do not edit Flyway history tables or reverse migrations by hand without a database backup.

Old images stay on disk. To free space, remove specific releases, never the current or previous one:

```bash
docker image rm ghcr.io/shmoonwalker/loc-{backend,data,frontend}:<old sha>
```

## One-time setup

### Server

Run as root on the server.

1. Check the tools and the architecture (the images are built for both `aarch64` and `x86_64`):

   ```bash
   uname -m; docker compose version; command -v curl flock tar
   ```

2. Create the deploy user and its directory, and copy (not move) the environment file:

   ```bash
   adduser --disabled-password --gecos "" deploy
   usermod -aG docker deploy
   install -d -o deploy -g deploy -m 750 /opt/loc
   install -o deploy -g deploy -m 600 /root/LOC/.env.production /opt/loc/.env.production
   ```

   Membership of the `docker` group is equivalent to root on this host; the user exists to separate
   deployments from your own root login, not to sandbox them. From now on edit `/opt/loc/.env.production`; a
   changed value takes effect on the next deployment.

3. Install the deploy key. Create it on your own machine (`ssh-keygen -t ed25519 -N "" -C loc-deploy -f loc-deploy`),
   copy `loc-deploy.pub` to the server and add it with `restrict` (no port forwarding, agent or terminal):

   ```bash
   install -d -o deploy -g deploy -m 700 /home/deploy/.ssh
   echo "restrict $(cat loc-deploy.pub)" >> /home/deploy/.ssh/authorized_keys
   chown deploy:deploy /home/deploy/.ssh/authorized_keys && chmod 600 /home/deploy/.ssh/authorized_keys
   ```

4. Get the host key and verify it. On your machine run `ssh-keyscan -t ed25519 62.238.119.8`. On the server
   run `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`, and compare that fingerprint with
   `ssh-keyscan -t ed25519 62.238.119.8 | ssh-keygen -lf -` on your machine. Then check the login:
   `ssh -i loc-deploy deploy@62.238.119.8 docker ps`.

5. Stop using `/root/LOC` for Compose. The first deployment takes over the existing `loc-prod` containers. A
   later `docker compose up` from the old checkout would replace them again with locally built images. Keep
   the directory as a backup, or remove it once a deployment has succeeded.

### GitHub

1. **Settings → Environments → New environment** `production`:
   - Required reviewers: none, so `main` deploys automatically.
   - Deployment branches and tags: **Selected branches and tags**, add `main`.
   - Environment secret `DEPLOY_SSH_KEY`: the full contents of the private key file `loc-deploy`.
   - Environment variables:

     | Name | Value |
     |---|---|
     | `DEPLOY_HOST` | `62.238.119.8` |
     | `DEPLOY_USER` | `deploy` |
     | `DEPLOY_PATH` | `/opt/loc` |
     | `DEPLOY_KNOWN_HOSTS` | the verified `ssh-keyscan` line(s) from step 4 |

2. **Settings → Actions → General → Workflow permissions** can stay on *Read repository contents*: the
   workflow asks for `packages: write` only in the jobs that push images, and `packages: read` in the deploy job.

3. **Packages.** The first run on `main` creates `loc-backend`, `loc-data` and `loc-frontend` under your
   account, linked to this repository. They are private by default, which works:
   - **Push:** the build jobs' `GITHUB_TOKEN` (`packages: write`). If a package with the same name already exists
     and is not linked, open it → **Package settings → Manage Actions access** → add this repository with *Write*.
   - **Pull:** the deploy job's own token (`packages: read`). It reaches the server only for the pull, in a temporary
     Docker config that is then removed, so the server stores no registry credential. To pull by hand on the
     server outside a deployment, either make the packages public (they contain no secrets) or run
     `docker login ghcr.io` as `deploy` with a classic personal access token limited to `read:packages`.
