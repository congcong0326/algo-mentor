#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd -P)"
KNOWLEDGE_DIR="${SCRIPT_DIR}"

usage() {
  cat <<'USAGE'
用法：
  ./import.sh                 校验并导入当前 knowledge-base
  ./import.sh --validate-only 只校验，不写数据库
  ./import.sh --force-rebuild 强制重建导入工具后再导入
  ./import.sh --help         查看帮助

说明：
  - 脚本固定把所在 knowledge-base 目录作为完整知识库快照导入。
  - 数据库连接沿用仓库根目录 Makefile 与 .env 中的 POSTGRES_* 配置。
  - 导入是全量替换共享知识库内容；用户复习状态和流水由后端导入器保留。
USAGE
}

ACTION="import"
FORCE_REBUILD="false"

for arg in "$@"; do
  case "${arg}" in
    --validate-only)
      ACTION="validate"
      ;;
    --force-rebuild)
      FORCE_REBUILD="true"
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "未知参数：${arg}" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! -f "${REPO_ROOT}/Makefile" || ! -f "${REPO_ROOT}/backend/pom.xml" ]]; then
  echo "无法定位 algo-mentor 仓库根目录：${REPO_ROOT}" >&2
  exit 1
fi

if [[ "${FORCE_REBUILD}" == "true" ]]; then
  echo "强制重建知识库导入工具..."
  make -C "${REPO_ROOT}" knowledge-cli-rebuild
fi

if [[ "${ACTION}" == "validate" ]]; then
  echo "校验知识库：${KNOWLEDGE_DIR}"
  make -C "${REPO_ROOT}" knowledge-validate KNOWLEDGE_DIR="${KNOWLEDGE_DIR}"
  exit 0
fi

echo "导入知识库：${KNOWLEDGE_DIR}"
make -C "${REPO_ROOT}" knowledge-import KNOWLEDGE_DIR="${KNOWLEDGE_DIR}"

if ! command -v psql >/dev/null 2>&1; then
  echo "未检测到 psql，跳过导入后数据库状态统计。"
  exit 0
fi

set +u
if [[ -f "${REPO_ROOT}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  . "${REPO_ROOT}/.env"
  set +a
fi
POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_DB="${POSTGRES_DB:-algo_mentor}"
POSTGRES_USER="${POSTGRES_USER:-algo_mentor}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-algo_mentor_dev}"
set -u

STATUS_SQL="SELECT status, count(*) FROM knowledge_card GROUP BY status ORDER BY status;"
DRAFT_SQL="SELECT count(*) FROM knowledge_card WHERE status='DRAFT';"

echo "导入后卡片状态统计："
if PGPASSWORD="${POSTGRES_PASSWORD}" psql \
  -h "${POSTGRES_HOST}" \
  -p "${POSTGRES_PORT}" \
  -U "${POSTGRES_USER}" \
  -d "${POSTGRES_DB}" \
  -X \
  -v ON_ERROR_STOP=1 \
  -P pager=off \
  -c "${STATUS_SQL}"; then
  draft_count="$(PGPASSWORD="${POSTGRES_PASSWORD}" psql \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    -X \
    -tA \
    -v ON_ERROR_STOP=1 \
    -c "${DRAFT_SQL}" 2>/dev/null || true)"
  if [[ "${draft_count}" =~ ^[0-9]+$ && "${draft_count}" -gt 0 ]]; then
    echo "提示：${draft_count} 张 DRAFT 卡片已导入数据库，但普通知识库页面默认只展示 PUBLISHED 内容。"
  fi
else
  echo "导入已完成；数据库状态统计失败，请检查本地 psql 或 POSTGRES_* 配置。" >&2
fi
