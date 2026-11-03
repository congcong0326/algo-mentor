#!/bin/sh
# 生成自部署 .env：已存在时不覆盖；新生成时把 change-me 占位替换为随机值。
set -eu

DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${DIR}/.env"

if [ -f "${ENV_FILE}" ]; then
  echo "已存在 ${ENV_FILE}，保持不变。"
  exit 0
fi

random_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
  else
    od -An -N32 -tx1 /dev/urandom | tr -d ' \n'
  fi
}

sed \
  -e "s/^POSTGRES_PASSWORD=change-me$/POSTGRES_PASSWORD=$(random_secret)/" \
  -e "s/^LEARNER_PROFILE_DOCUMENT_STATEMENT_REF_HMAC_SECRET=change-me$/LEARNER_PROFILE_DOCUMENT_STATEMENT_REF_HMAC_SECRET=$(random_secret)/" \
  "${DIR}/.env.example" > "${ENV_FILE}"
chmod 600 "${ENV_FILE}"
echo "已生成 ${ENV_FILE}，请把 AUTH_ADMIN_EMAILS 改成你自己的邮箱。"
