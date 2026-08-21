#!/usr/bin/env bash

# 自动选择预发布发布路径：无 Flyway 迁移时使用快速发布；有迁移时拒绝误走快速路径。
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly PREPROD_HOST="${PREPROD_HOST:-leetmentor-root}"
readonly PREPROD_CONTAINER_NAME="${PREPROD_CONTAINER_NAME:-algo-mentor}"

fail() {
  echo "Preprod deployment aborted: $*" >&2
  exit 1
}

remote_command() {
  ssh -o BatchMode=yes -o ConnectTimeout=8 "${PREPROD_HOST}" "$@"
}

resolve_base_ref() {
  if [[ -n "${PREPROD_BASE_REF:-}" ]]; then
    printf '%s\n' "${PREPROD_BASE_REF}"
    return
  fi

  local remote_commit
  remote_commit="$(remote_command "sudo docker container inspect -f '{{ index .Config.Labels \"org.congcong.algomentor.commit\" }}' '${PREPROD_CONTAINER_NAME}' 2>/dev/null || true" | tr -d '\r' | tail -n 1)"
  if [[ -n "${remote_commit}" && "${remote_commit}" != "<no value>" ]]; then
    printf '%s\n' "${remote_commit}"
    return
  fi

  [[ -n "${PREPROD_BOOTSTRAP_BASE_REF:-}" ]] || {
    fail "the running container has no commit label; set PREPROD_BASE_REF or PREPROD_BOOTSTRAP_BASE_REF."
  }
  printf '%s\n' "${PREPROD_BOOTSTRAP_BASE_REF}"
}

main() {
  command -v git >/dev/null 2>&1 || fail 'git is unavailable.'
  command -v ssh >/dev/null 2>&1 || fail 'ssh is unavailable.'

  case "${1:-}" in
    ''|--preflight)
      ;;
    *)
      fail "unsupported argument: $1"
      ;;
  esac

  local release_ref="${PREPROD_RELEASE_REF:-HEAD}"
  git -C "${REPOSITORY_ROOT}" rev-parse --verify "${release_ref}^{commit}" >/dev/null \
    || fail "release ref is not a commit: ${release_ref}"

  local base_ref migration_files
  base_ref="$(resolve_base_ref)"
  git -C "${REPOSITORY_ROOT}" rev-parse --verify "${base_ref}^{commit}" >/dev/null \
    || fail "base ref is not a local commit: ${base_ref}"
  migration_files="$(git -C "${REPOSITORY_ROOT}" diff --name-only "${base_ref}" "${release_ref}" -- \
    ':(glob)backend/**/src/main/resources/db/migration/**')"

  if [[ -n "${migration_files}" ]]; then
    echo 'Flyway migration files changed:' >&2
    printf '%s\n' "${migration_files}" >&2
    fail 'database-aware preprod deployment is required; refusing the fast path.'
  fi

  echo "No Flyway migration files changed between ${base_ref} and ${release_ref}; selecting fast preprod deployment."
  exec bash "${SCRIPT_DIR}/deploy-preprod-fast.sh" "$@"
}

main "$@"
