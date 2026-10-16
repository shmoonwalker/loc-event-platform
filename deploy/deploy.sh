#!/usr/bin/env bash
# Rolls out one LOC release on the production server. The Deploy workflow copies this script and
# docker-compose.prod.yml to <deploy dir>/releases/<git sha>/ and runs:
#
#   deploy.sh pull [registry user]   pulls this release's images (registry token on stdin when a user is given)
#   deploy.sh                        replaces the LOC containers with this release and checks them
#
# Running releases/<older sha>/deploy.sh rolls back to that release with its own Compose file.
set -euo pipefail

release_dir=$(cd "$(dirname "$0")" && pwd)
tag=${release_dir##*/}
loc_dir=$(dirname "$(dirname "$release_dir")")
[[ $tag =~ ^[0-9a-f]{40}$ ]] || { echo "Run this from <deploy dir>/releases/<git sha>/" >&2; exit 2; }

export LOC_IMAGE_TAG=$tag
compose=(docker compose -f "$release_dir/docker-compose.prod.yml" --env-file "$loc_dir/.env.production")
images=$("${compose[@]}" config --images)
mapfile -t images <<< "$images"

log() { printf '%s %s\n' "$(date -u +%FT%TZ)" "$*"; }
die() { log "ERROR: $*"; exit 1; }

if [[ ${1:-} == pull ]]; then
  docker_config=()
  if [[ -n ${2:-} ]]; then
    # A throwaway Docker config keeps the short-lived registry token off the server after the pull.
    tmp=$(mktemp -d)
    trap 'rm -rf "$tmp"' EXIT
    docker --config "$tmp" login ghcr.io -u "$2" --password-stdin
    docker_config=(--config "$tmp")
  fi
  for image in "${images[@]}"; do docker "${docker_config[@]}" pull "$image"; done
  exit 0
fi

# Waits for a deployment started by hand or by an earlier workflow run.
exec 9> "$loc_dir/deploy.lock"
flock -n 9 || { log "Waiting for another deployment to finish"; flock -w 1800 9; }

previous=$(readlink "$loc_dir/current" || true)
previous=${previous##*/}
log "Deploying $tag on $(uname -m); previous release: ${previous:-none}"

# Nothing is stopped unless every image of the release is already on the server.
docker image inspect "${images[@]}" > /dev/null || die "Images missing; run deploy.sh pull first"

on_exit() {
  local status=$?
  (( status == 0 )) && return
  log "Deployment of $tag FAILED. LOC containers now:"
  docker ps -a --filter label=com.docker.compose.project=loc-prod \
    --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}' || true
  for container in loc-data loc-backend loc-frontend; do
    echo "----- $container: last 100 log lines"
    docker logs --tail 100 "$container" 2>&1 || true
  done
  echo "$(date -u +%FT%TZ) $tag failed" >> "$loc_dir/deploy-history"
  [[ -z $previous ]] || log "Previous release: $loc_dir/releases/$previous/deploy.sh (check migrations before rolling back)"
}
trap on_exit EXIT

state() { docker inspect -f '{{.State.Status}} {{.RestartCount}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' "$1"; }

# Fails as soon as the container stops or restarts, so a restart loop is never mistaken for a slow start.
wait_for() { # <container> <seconds> <expected state>
  local deadline=$((SECONDS + $2)) current
  while current=$(state "$1"); [[ $current != "$3" ]]; do
    [[ $current == "running 0"* ]] || die "$1 is not running cleanly: $current"
    (( SECONDS < deadline )) || die "$1 did not reach '$3' within $2s: $current"
    sleep 5
  done
}

# --force-recreate stops the old container before starting the new one: never two data workers at once.
up() { "${compose[@]}" up -d --no-deps --force-recreate --pull never "$1"; }

# Data first: its Flyway migrations own the publication schema the backend reads.
log "Replacing loc-data"
up loc-data
deadline=$((SECONDS + 300))
until docker logs loc-data 2>&1 | grep 'Started .* in .* seconds' > /dev/null; do
  [[ $(state loc-data) == "running 0"* ]] || die "loc-data stopped or restarted during start-up (migrations?)"
  (( SECONDS < deadline )) || die "loc-data did not finish starting within 300s"
  sleep 5
done

log "Replacing loc-backend"
up loc-backend
wait_for loc-backend 300 "running 0 healthy"

log "Replacing loc-frontend"
up loc-frontend
wait_for loc-frontend 120 "running 0 healthy"

# Through the frontend proxy to the backend and the database, as Cloudflare Tunnel reaches it.
for path in / /api/categories; do
  curl -fsS -o /dev/null --max-time 10 --retry 5 --retry-delay 3 --retry-all-errors "http://127.0.0.1:3001$path" \
    || die "GET http://127.0.0.1:3001$path failed"
done

# The data worker has kept running through the backend and frontend rollout; it must not have restarted.
[[ $(state loc-data) == "running 0"* ]] || die "loc-data is not running cleanly: $(state loc-data)"

ln -sfn "releases/$tag" "$loc_dir/current"
echo "$(date -u +%FT%TZ) $tag ok" >> "$loc_dir/deploy-history"
log "Deployed $tag"
