# 题库 Seed 数据导入说明

本文说明如何复用当前仓库已经沉淀的题库 seed 成果，在新的本地开发环境中快速导入题库与公司高频题信号。

## 当前成果

当前仓库保留以下可复用数据：

- `data/seed/problems.jsonl`：最终题目 seed，包含 `3591` 道题。
- `data/seed/problem_categories.jsonl`、`data/seed/problem_category_items.jsonl`：题目分类 seed。
- `data/company-seed/problem_company_signals.jsonl`：公司题目信号 seed，包含 `33138` 条可入库信号。
- `data/index/problem_index.jsonl`：题目索引快照。
- `data/reports/problem_seed_validation_report.json`：题目 seed 校验报告。
- `data/reports/problem_company_seed_report.json`：公司题目信号生成报告。

题目 seed 的内容状态分布：

| contentStatus | 数量 |
| --- | ---: |
| `BILINGUAL` | 3202 |
| `CN_ONLY` | 389 |

公司信号覆盖 `441` 家公司，其中 `CHINA=7`、`OTHER=434`。

## 版本控制策略

仓库会提交最终导入所需的 seed、index 和 report 文件，便于换环境后直接导入。

`data/sources/` 仍然不纳入版本控制。该目录用于保存 LeetCode 原始抓取缓存，体积较大，并且不是执行本地导入的必需输入。只有需要重新生成最终 seed 或审计原始抓取数据时，才需要重新准备该目录。

## 新环境导入步骤

### 1. 准备 PostgreSQL

如果当前开发容器或机器还没有本地 PostgreSQL，可以先执行：

```bash
make db-install
```

默认数据库配置为：

| 配置 | 默认值 |
| --- | --- |
| `POSTGRES_HOST` | `localhost` |
| `POSTGRES_PORT` | `5432` |
| `POSTGRES_DB` | `algo_mentor` |
| `POSTGRES_USER` | `algo_mentor` |
| `POSTGRES_PASSWORD` | `algo_mentor_dev` |

### 2. 导入题库与公司信号

使用仓库内已提交的 seed 数据执行：

```bash
make db-seed POSTGRES_HOST=localhost POSTGRES_PORT=5432 POSTGRES_DB=algo_mentor POSTGRES_USER=algo_mentor POSTGRES_PASSWORD=algo_mentor_dev
```

该命令会启动后端 seed runner，并执行 Flyway 迁移。导入顺序为：

1. 导入 `data/seed` 中的题目与分类。
2. 导入 `data/company-seed` 中的公司题目信号。

导入逻辑是 upsert 幂等写入。重复执行不会重复插入题目、公司或信号；每次公司信号导入会新增一条 `problem_company_import_run` 审计记录。

### 3. 验证导入结果

可以执行以下 SQL 检查核心数据：

```bash
PGPASSWORD=algo_mentor_dev psql -h localhost -p 5432 -U algo_mentor -d algo_mentor -v ON_ERROR_STOP=1 \
  -c "select content_status, count(*) from problem group by content_status order by content_status;" \
  -c "select company_market, count(*) from company group by company_market order by company_market;" \
  -c "select count(*) as problem_company_signal_count from problem_company_signal;" \
  -c "select p.content_status, count(*) from problem_company_signal pcs join problem p on p.id = pcs.problem_id group by p.content_status order by p.content_status;" \
  -c "select count(*) as problem_company_import_run_count from problem_company_import_run;"
```

全新数据库导入后，核心预期为：

- `problem_company_signal`：`33138`
- `company`：`CHINA=7`、`OTHER=434`
- `problem_company_import_run`：每执行一次 `make db-seed` 增加 `1`

如果本地库已有旧题目数据，`problem` 总数或 `BILINGUAL` 数量可能大于当前 seed 的 `3202`。当前导入策略不会硬删除旧业务数据，只会 upsert 当前 seed 内的题目，并在必要时清理旧 slug 上冲突的 `frontend_id` / `frontend_display_id`。

## 重新生成 Seed

通常不需要重新生成 seed。只有当需要更新题库来源、重新抓取 LeetCode 数据或调整 seed 生成逻辑时，才执行本节流程。

重新生成题目 seed 需要准备：

- `data/index/problem_index.jsonl`
- `data/sources/leetcode-api/*.json`

执行：

```bash
python3 -m tools.problem_seed.prepare_seed \
  --index data/index/problem_index.jsonl \
  --cache-dir data/sources/leetcode-api \
  --output-dir data/seed

python3 -m tools.problem_seed.validate_seed \
  --seed data/seed/problems.jsonl \
  --output data/reports/problem_seed_validation_report.json

python3 -m tools.problem_company_seed.prepare_company_seed \
  --local-problems data/seed/problems.jsonl \
  --output-dir data/company-seed \
  --report data/reports/problem_company_seed_report.json
```

重新生成后至少运行：

```bash
python3 -m unittest discover -s tools -p '*_test.py'
make backend-test
make frontend-test
```

## 注意事项

- 不要提交密钥、真实用户数据、数据库密码或本地 `.env`。
- 不要把 `data/sources/` 作为默认提交内容；它是原始缓存，不是快速导入的必要成果。
- 不要手动删除本地旧题目数据来追求数量完全一致，除非当前任务明确要求重置数据库。
- 如果导入失败，优先查看 Flyway 迁移状态、PostgreSQL 连接参数，以及 `data/seed` / `data/company-seed` 文件是否存在。
