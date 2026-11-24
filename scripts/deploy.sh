#!/usr/bin/env bash
set -Eeuo pipefail

# SSM runs as root. The environment file stays on the instance and is never sent to GitHub.
image=${1:?Provide the image URI}
candidate_compose=${2:?Provide the candidate Compose file}
deploy_dir=${DEPLOY_DIR:-/opt/jikchin}
env_file="$deploy_dir/.env.prod"
active_compose="$deploy_dir/compose.prod.yaml"

[[ "$image" =~ ^[0-9]{12}\.dkr\.ecr\.ap-northeast-2\.amazonaws\.com/jikchin-app:[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$ ]] || { echo 'Invalid image URI.' >&2; exit 1; }
[[ -f "$env_file" && -f "$candidate_compose" ]] || { echo 'Missing production environment or Compose file.' >&2; exit 1; }

exec 9>"$deploy_dir/.deploy.lock"
flock -n 9 || { echo 'Another deployment is running.' >&2; exit 1; }
export APP_IMAGE="$image"
compose=(docker compose --project-name jikchin-prod --project-directory "$deploy_dir" --env-file "$env_file" -f "$candidate_compose")
"${compose[@]}" config --quiet

container=$("${compose[@]}" ps --all --quiet app)
previous_image=''
previous_running='false'
if [[ -n "$container" ]]; then
  previous_image=$(docker inspect --format '{{.Config.Image}}' "$container")
  previous_running=$(docker inspect --format '{{.State.Running}}' "$container")
fi
rollback_compose="$candidate_compose"
rollback_dir=$(mktemp -d)
trap 'rm -rf "$rollback_dir"' EXIT
if [[ -f "$active_compose" ]]; then
  cp "$active_compose" "$rollback_dir/compose.prod.yaml"
  rollback_compose="$rollback_dir/compose.prod.yaml"
fi

rollback() {
  echo 'Deployment failed; attempting to restore the previous app.' >&2
  if [[ "$previous_running" == 'true' && -n "$previous_image" ]]; then
    APP_IMAGE="$previous_image" docker compose --project-name jikchin-prod --project-directory "$deploy_dir" --env-file "$env_file" -f "$rollback_compose" up -d --no-deps --no-build app || echo 'Rollback failed; inspect the EC2 container logs.' >&2
  else
    "${compose[@]}" stop app || true
    echo 'There was no running previous app to restore.' >&2
  fi
}

registry=${image%%/*}
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin "$registry"
"${compose[@]}" pull app

if ! "${compose[@]}" up -d --no-deps --no-build app; then
  rollback
  exit 1
fi

endpoint=$("${compose[@]}" port app 8080 || true)
port=${endpoint##*:}
healthy=false
if [[ "$port" =~ ^[0-9]+$ ]]; then
  for ((attempt=1; attempt<=60; attempt++)); do
    if curl --fail --silent --show-error --max-time 3 "http://127.0.0.1:$port/v3/api-docs" -o /dev/null; then
      healthy=true
      break
    fi
    sleep 2
  done
fi
if [[ "$healthy" != 'true' ]]; then
  echo 'Application did not pass the HTTP startup check.' >&2
  rollback
  exit 1
fi

# Keep manual Compose commands aligned with the successfully deployed image.
# Do not print or interpret any other values in the secret environment file.
if ! python3 - "$env_file" "$image" "$candidate_compose" "$active_compose" <<'PY'
import os
from pathlib import Path
import sys
import tempfile

env_path, image, candidate, active = Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3]), Path(sys.argv[4])
original = env_path.read_text()
lines = original.splitlines(keepends=True)
if sum(line.startswith('APP_IMAGE=') for line in lines) != 1:
    raise ValueError('The environment file must contain exactly one APP_IMAGE entry.')
updated = ''.join('APP_IMAGE=' + image + '\n' if line.startswith('APP_IMAGE=') else line for line in lines)
temporary = []
try:
    for path, contents, mode in ((active, candidate.read_bytes(), 0o644),
                                 (env_path, updated.encode(), env_path.stat().st_mode & 0o777)):
        with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as target:
            temporary.append(target.name)
            os.fchmod(target.fileno(), mode)
            target.write(contents)
    os.replace(temporary[0], active)
    os.replace(temporary[1], env_path)
finally:
    for path in temporary:
        if os.path.exists(path):
            os.unlink(path)
PY
then
  echo 'Could not save the deployed configuration.' >&2
  rollback
  exit 1
fi
echo "Deployment succeeded: $image"
