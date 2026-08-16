#!/usr/bin/env bash

# 发布不含数据库迁移的已提交版本到预发布环境。运行时密钥和参数始终留在目标机。
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly PREPROD_HOST="${PREPROD_HOST:-leetmentor-dev}"
readonly PREPROD_CONTAINER_NAME="${PREPROD_CONTAINER_NAME:-algo-mentor}"
readonly PREPROD_RUNTIME_DIR="${PREPROD_RUNTIME_DIR:-/etc/algo-mentor}"
readonly PREPROD_RELEASE_ROOT="${PREPROD_RELEASE_ROOT:-/opt/algo-mentor/releases}"
readonly PREPROD_PORT="${PREPROD_PORT:-18080}"
readonly RUNTIME_CONTRACT="${REPOSITORY_ROOT}/deploy/docker/preprod-runtime-env.required"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command is unavailable: $1" >&2
    exit 1
  }
}

fail() {
  echo "Fast preprod deployment aborted: $*" >&2
  exit 1
}

validate_deployment_parameters() {
  [[ "${PREPROD_HOST}" =~ ^[A-Za-z0-9][A-Za-z0-9.-]*$ ]] \
    || fail "PREPROD_HOST must be an SSH host alias or hostname."
  [[ "${PREPROD_CONTAINER_NAME}" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] \
    || fail "PREPROD_CONTAINER_NAME is invalid."
  [[ "${PREPROD_PORT}" =~ ^[1-9][0-9]{0,4}$ && "${PREPROD_PORT}" -le 65535 ]] \
    || fail "PREPROD_PORT must be a valid TCP port."
  [[ "${PREPROD_RUNTIME_DIR}" =~ ^/[A-Za-z0-9_./-]*$ ]] \
    || fail "PREPROD_RUNTIME_DIR must be an absolute path without shell metacharacters."
  [[ "${PREPROD_RELEASE_ROOT}" =~ ^/[A-Za-z0-9_./-]*$ ]] \
    || fail "PREPROD_RELEASE_ROOT must be an absolute path without shell metacharacters."
}

remote_command() {
  ssh -tt "${PREPROD_HOST}" "$@"
}

validate_worktree() {
  local -a release_input_paths=(
    Makefile
    pom.xml
    backend
    frontend
    deploy/docker/Dockerfile.preprod
    deploy/docker/preprod-runtime-env.required
    scripts/deploy-preprod-fast.sh
  )
  git -C "${REPOSITORY_ROOT}" diff --quiet -- "${release_input_paths[@]}" \
    || fail "commit or stash tracked application-release changes first."
  git -C "${REPOSITORY_ROOT}" diff --cached --quiet -- "${release_input_paths[@]}" \
    || fail "commit or unstage indexed application-release changes first."

  local untracked_file
  while IFS= read -r untracked_file; do
    case "${untracked_file}" in
      backend/*|frontend/*|deploy/docker/Dockerfile.preprod|deploy/docker/preprod-runtime-env.required|scripts/deploy-preprod-fast.sh)
        fail "commit or remove untracked application-release input: ${untracked_file}"
        ;;
    esac
  done < <(git -C "${REPOSITORY_ROOT}" ls-files --others --exclude-standard)
}

read_remote_commit() {
  remote_command \
    "sudo docker container inspect -f '{{ index .Config.Labels \"org.congcong.algomentor.commit\" }}' '${PREPROD_CONTAINER_NAME}' 2>/dev/null || true" \
    | tr -d '\r' \
    | tail -n 1
}

resolve_base_ref() {
  if [[ -n "${PREPROD_BASE_REF:-}" ]]; then
    printf '%s\n' "${PREPROD_BASE_REF}"
    return
  fi

  local remote_commit
  remote_commit="$(read_remote_commit)"
  [[ -n "${remote_commit}" && "${remote_commit}" != "<no value>" ]] || {
    fail "the running container has no release label. Run once with PREPROD_BASE_REF=<currently deployed commit>."
  }
  printf '%s\n' "${remote_commit}"
}

validate_runtime_contract() {
  [[ -f "${RUNTIME_CONTRACT}" ]] || fail "runtime contract is missing: ${RUNTIME_CONTRACT}"

  if ! awk -F: '
    /^[[:space:]]*($|#)/ { next }
    NF != 2 || $1 !~ /^[A-Za-z0-9_.-]+\.env$/ || $2 !~ /^[A-Z][A-Z0-9_]*$/ { exit 1 }
  ' "${RUNTIME_CONTRACT}"; then
    fail "runtime contract format is invalid: ${RUNTIME_CONTRACT}"
  fi

  local strict_preprod_variables
  strict_preprod_variables="$({
    rg --no-filename -o '\$\{[A-Z][A-Z0-9_]*\}' \
      "${REPOSITORY_ROOT}"/backend/**/src/main/resources/application-preprod.yml \
      2>/dev/null || true
  } | sed -E 's/^\$\{([^}]+)\}$/\1/' | sort -u)"

  local contract_variables missing_variables
  contract_variables="$(awk -F: '/^[[:space:]]*($|#)/ { next } { print $2 }' "${RUNTIME_CONTRACT}" | sort -u)"
  missing_variables="$(comm -23 <(printf '%s\n' "${strict_preprod_variables}") <(printf '%s\n' "${contract_variables}"))"
  [[ -z "${missing_variables}" ]] || {
    echo "The following required preprod variables are not represented in ${RUNTIME_CONTRACT}:" >&2
    printf '%s\n' "${missing_variables}" >&2
    fail "update the runtime contract before deployment."
  }
}

validate_no_migrations() {
  local base_ref="$1"
  local release_ref="$2"
  local migration_files
  migration_files="$(git -C "${REPOSITORY_ROOT}" diff --name-only "${base_ref}" "${release_ref}" -- \
    ':(glob)backend/**/src/main/resources/db/migration/**')"
  [[ -z "${migration_files}" ]] || {
    echo "The following Flyway migration files changed:" >&2
    printf '%s\n' "${migration_files}" >&2
    fail "use the full database-aware deployment process for schema or data migrations."
  }
}

run_relevant_tests() {
  local base_ref="$1"
  local release_ref="$2"
  local changed_files
  changed_files="$(git -C "${REPOSITORY_ROOT}" diff --name-only "${base_ref}" "${release_ref}")"

  if grep -q '^frontend/' <<<"${changed_files}"; then
    make -C "${REPOSITORY_ROOT}" frontend-test
  fi

  if grep -q '^backend/' <<<"${changed_files}"; then
    make -C "${REPOSITORY_ROOT}" backend-test
  fi
}

stage_release() {
  local release_ref="$1"
  local release_id="$2"
  local stage_dir="$3"

  make -C "${REPOSITORY_ROOT}" package-skip-tests

  local application_jar
  application_jar="$(find "${REPOSITORY_ROOT}/backend/mentor-api/target" -maxdepth 1 -type f \
    -name 'mentor-api-*.jar' ! -name '*.original' | sort | tail -n 1)"
  [[ -n "${application_jar}" ]] || fail "cannot find packaged mentor-api JAR."

  cp "${application_jar}" "${stage_dir}/mentor-api.jar"
  cp "${REPOSITORY_ROOT}/deploy/docker/Dockerfile.preprod" "${stage_dir}/Dockerfile"
  cp "${RUNTIME_CONTRACT}" "${stage_dir}/preprod-runtime-env.required"
  git -C "${REPOSITORY_ROOT}" rev-parse "${release_ref}" > "${stage_dir}/commit.txt"
  sha256sum "${stage_dir}/mentor-api.jar" > "${stage_dir}/mentor-api.jar.sha256"
  printf '%s\n' "${release_id}" > "${stage_dir}/release-id.txt"
}

deploy_remote_release() {
  local release_ref="$1"
  local release_id="$2"
  local stage_dir="$3"
  local remote_stage="/tmp/algo-mentor-release-${release_id}"

  remote_command "mkdir -p '${remote_stage}'"
  scp -q \
    "${stage_dir}/mentor-api.jar" \
    "${stage_dir}/Dockerfile" \
    "${stage_dir}/preprod-runtime-env.required" \
    "${stage_dir}/commit.txt" \
    "${stage_dir}/mentor-api.jar.sha256" \
    "${stage_dir}/release-id.txt" \
    "${PREPROD_HOST}:${remote_stage}/"

  remote_command "sudo /bin/bash -s" <<REMOTE_SCRIPT
set -euo pipefail

readonly release_id='${release_id}'
readonly release_ref='${release_ref}'
readonly remote_stage='${remote_stage}'
readonly release_root='${PREPROD_RELEASE_ROOT}'
readonly runtime_dir='${PREPROD_RUNTIME_DIR}'
readonly container_name='${PREPROD_CONTAINER_NAME}'
readonly application_port='${PREPROD_PORT}'
readonly release_dir="\${release_root}/\${release_id}"
readonly image_name="algo-mentor-api:preprod-\${release_id}"
readonly previous_container_name="\${container_name}-previous-\${release_id}"

rollback_needed=false
previous_container_stopped=false
previous_container_renamed=false
replacement_container_may_exist=false

rollback() {
  local exit_code=\$?
  if [[ "\${rollback_needed}" == true ]]; then
    if [[ "\${replacement_container_may_exist}" == true ]]; then
      docker rm -f "\${container_name}" >/dev/null 2>&1 || true
    fi
    if [[ "\${previous_container_renamed}" == true ]]; then
      docker rename "\${previous_container_name}" "\${container_name}" >/dev/null 2>&1 || true
      docker start "\${container_name}" >/dev/null 2>&1 || true
    elif [[ "\${previous_container_stopped}" == true ]]; then
      docker start "\${container_name}" >/dev/null 2>&1 || true
    fi
  fi
  exit "\${exit_code}"
}
trap rollback ERR

[[ -d "\${remote_stage}" ]] || { echo 'Uploaded release staging directory is missing.' >&2; exit 1; }
[[ -f "\${remote_stage}/mentor-api.jar" ]] || { echo 'Uploaded JAR is missing.' >&2; exit 1; }
[[ -f "\${remote_stage}/Dockerfile" ]] || { echo 'Uploaded Dockerfile is missing.' >&2; exit 1; }

sha256sum -c "\${remote_stage}/mentor-api.jar.sha256" >/dev/null

while IFS=: read -r environment_file environment_key; do
  [[ -z "\${environment_file}" || "\${environment_file}" == \#* ]] && continue
  if ! awk -F= -v wanted="\${environment_key}" '
    /^[[:space:]]*#/ { next }
    \$1 == wanted && length(\$2) > 0 { found = 1 }
    END { exit found ? 0 : 1 }
  ' "\${runtime_dir}/\${environment_file}"; then
    echo "Missing required preprod environment variable: \${environment_file}:\${environment_key}" >&2
    exit 1
  fi
done < "\${remote_stage}/preprod-runtime-env.required"

install -d -m 0755 "\${release_dir}"
install -m 0644 "\${remote_stage}/mentor-api.jar" "\${release_dir}/mentor-api.jar"
install -m 0644 "\${remote_stage}/Dockerfile" "\${release_dir}/Dockerfile"
install -m 0644 "\${remote_stage}/commit.txt" "\${release_dir}/commit.txt"
install -m 0644 "\${remote_stage}/mentor-api.jar.sha256" "\${release_dir}/mentor-api.jar.sha256"

docker build --pull -t "\${image_name}" "\${release_dir}"

if docker container inspect "\${container_name}" >/dev/null 2>&1; then
  rollback_needed=true
  docker stop "\${container_name}" >/dev/null
  previous_container_stopped=true
  docker rename "\${container_name}" "\${previous_container_name}"
  previous_container_renamed=true
fi

replacement_container_may_exist=true
docker run -d \
  --name "\${container_name}" \
  --restart unless-stopped \
  --label "org.congcong.algomentor.commit=\${release_ref}" \
  --label "org.congcong.algomentor.release=\${release_id}" \
  --env-file "\${runtime_dir}/database.env" \
  --env-file "\${runtime_dir}/redis.env" \
  --env-file "\${runtime_dir}/runtime.env" \
  --network bridge \
  -p "\${application_port}:\${application_port}" \
  -e "API_PORT=\${application_port}" \
  -e 'SPRING_PROFILES_ACTIVE=preprod' \
  "\${image_name}" >/dev/null

for attempt in \$(seq 1 30); do
  if curl --fail --silent --show-error "http://127.0.0.1:\${application_port}/actuator/health/readiness" \
      | grep -q '"status":"UP"'; then
    rollback_needed=false
    rm -rf "\${remote_stage}"
    echo "Preprod release \${release_id} is healthy."
    exit 0
  fi
  sleep 2
done

echo 'Readiness check did not become UP within 60 seconds.' >&2
false
REMOTE_SCRIPT
}

main() {
  require_command git
  require_command make
  require_command ssh
  require_command scp
  require_command sha256sum
  require_command rg

  validate_deployment_parameters
  validate_worktree
  validate_runtime_contract

  local release_ref base_ref release_commit release_id stage_dir
  release_ref="${PREPROD_RELEASE_REF:-HEAD}"
  git -C "${REPOSITORY_ROOT}" rev-parse --verify "${release_ref}^{commit}" >/dev/null \
    || fail "release ref is not a commit: ${release_ref}"
  base_ref="$(resolve_base_ref)"
  git -C "${REPOSITORY_ROOT}" rev-parse --verify "${base_ref}^{commit}" >/dev/null \
    || fail "base ref is not a local commit: ${base_ref}"

  validate_no_migrations "${base_ref}" "${release_ref}"
  run_relevant_tests "${base_ref}" "${release_ref}"

  release_commit="$(git -C "${REPOSITORY_ROOT}" rev-parse "${release_ref}")"
  release_id="$(git -C "${REPOSITORY_ROOT}" rev-parse --short=12 "${release_ref}")-$(date -u +%Y%m%dT%H%M%SZ)"
  stage_dir="$(mktemp -d)"
  trap 'rm -rf "${stage_dir}"' EXIT

  stage_release "${release_ref}" "${release_id}" "${stage_dir}"
  deploy_remote_release "${release_commit}" "${release_id}" "${stage_dir}"
}

main "$@"
