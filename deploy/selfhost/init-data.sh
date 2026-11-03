#!/bin/sh
# 首次启动数据初始化：由 compose 的 init 服务执行，完成后 app 服务才会启动。
# 只调用应用已有的种子导入与知识库导入入口，不修改任何业务逻辑。
#  - 已检测到题库、学习计划模板和知识库数据时直接跳过；
#  - 设置 SEED_FORCE=true 时强制重新导入（导入均为幂等 upsert，可用于升级后同步数据）。
set -eu

DATA_DIR=/app/data
KNOWLEDGE_DIR=/app/knowledge-base
export PGHOST="${POSTGRES_HOST}" PGPORT="${POSTGRES_PORT}" PGDATABASE="${POSTGRES_DB}"
export PGUSER="${POSTGRES_USER}" PGPASSWORD="${POSTGRES_PASSWORD}"

already_seeded() {
  result="$(psql -tAq -v ON_ERROR_STOP=1 -c "
    SELECT (SELECT count(*) FROM problem) > 0
       AND (SELECT count(*) FROM learning_plan_template) > 0
       AND (SELECT count(*) FROM knowledge_outline_node) > 0" 2>/dev/null || true)"
  [ "${result}" = "t" ]
}

if [ "${SEED_FORCE:-false}" != "true" ] && already_seeded; then
  echo "[init] 已存在初始数据，跳过导入。如需重新导入请设置 SEED_FORCE=true。"
  exit 0
fi

# 题目元数据（分类/提示/代码模板/相关题）导入会校验 manifest 引用的抓取报告
# data/sources/leetcode-api-metadata/problem_metadata_fetch_report.json（已随镜像提供）。
# 如需跳过元数据导入，可设置 SEED_PROBLEM_METADATA=false。
METADATA_ENABLED="${SEED_PROBLEM_METADATA:-true}"

echo "[init] 导入题库、公司标签、题目元数据(${METADATA_ENABLED})和学习计划模板（首次执行 Flyway 迁移）..."
java -jar /app/app.jar \
  --server.port=0 \
  --spring.flyway.enabled=true \
  --algo-mentor.problem.seed.enabled=true \
  --algo-mentor.problem.seed.path="${DATA_DIR}/seed" \
  --algo-mentor.problem.insight-seed.path="${DATA_DIR}/problem-insight-seed" \
  --algo-mentor.problem.company-seed.enabled=true \
  --algo-mentor.problem.company-seed.path="${DATA_DIR}/company-seed" \
  --algo-mentor.problem.metadata-seed.enabled="${METADATA_ENABLED}" \
  --algo-mentor.problem.metadata-seed.path="${DATA_DIR}/problem-metadata-seed" \
  --algo-mentor.learning-plan-template.seed.enabled=true \
  --algo-mentor.learning-plan-template.seed.path="${DATA_DIR}/learning-plan-template-seed"

echo "[init] 导入知识库..."
java -jar /app/app.jar --knowledge-import "${KNOWLEDGE_DIR}"

echo "[init] 初始数据导入完成。"
