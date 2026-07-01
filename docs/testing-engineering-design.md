# 测试工程设计

## 背景

`algo-mentor` 当前已有后端 Maven 单元测试和前端 Vitest 测试，但缺少面向已启动服务的黑盒冒烟测试入口。后续项目还需要做模型效果评测，因此测试工程需要同时满足两类目标：

- 第一阶段快速验证接口和核心业务流程是否可用。
- 为后续模型 eval 保留 Python 生态的扩展空间。

本文只定义测试工程设计，不包含具体实现计划。

## 目标

- 根目录通过 `make test` 触发冒烟测试。
- 执行前默认服务已经由开发者或 CI 启动，`make test` 不负责启动后端、前端、数据库或外部 AI 服务。
- 第一阶段覆盖后端 API 与业务流程的冒烟测试，不做浏览器级 UI E2E。
- 冒烟测试必须跑真实写入与读回链路，不能退化为健康检查。
- 测试工程使用 Python 3.12，Python 依赖与命令入口使用 `uv` 管理。
- HTTP 流程优先用 Hurl 表达，复杂编排与后续模型 eval 使用 Python 承接。

## 非目标

- 不替代 `make backend-test` 和 `make frontend-test`。
- 不在 `make test` 中执行完整单元测试、构建、打包或前端浏览器测试。
- 不把模型质量评测放进 `make test` 默认链路。
- 第一阶段不直接校验真实大模型输出质量，避免把外部模型波动引入基础冒烟测试。

## 技术选型

### Hurl

Hurl 作为 API 冒烟测试的主表达工具。

优势：

- 文件形式接近 HTTP 契约，适合 code review。
- 原生支持请求串联、变量、capture、JSONPath 断言、cookie/session 和测试报告。
- 比 Bash + curl + jq 更易维护，失败位置更清晰。
- 比 Java Failsafe/RestAssured 更轻量，更符合“已启动服务的黑盒 smoke”定位。

边界：

- 不适合复杂条件分支、循环轮询、清理保障、多用户并发编排和复杂 SSE 解析。
- 不承担模型 eval 的数据集管理、评分器和统计汇总。

### Python 3.12 + uv

Python 作为测试工程的编排与扩展语言，`uv` 作为依赖和命令管理工具。

职责：

- 生成 smoke run 变量，例如 `run_id`、测试邮箱、测试密码、base URL。
- 调用 Hurl 并传入变量。
- 统一输出日志、退出码和报告位置。
- 在 Hurl 不适合表达时承接复杂 smoke case。
- 后续承接模型 eval runner、数据集读取、评分器、结果聚合和阈值判断。

选用 Python 3.12 的原因：

- 与主工程语言解耦，不增加后端 Maven 测试套件复杂度。
- LLM eval 生态更成熟，便于后续接入 Promptfoo、DeepEval、自定义 LLM judge 或项目内评分器。
- 对 JSON、JSONL、HTTP、并发、报告生成和数据处理支持成熟。

### 不选 Java Failsafe 作为第一阶段主方案

Java Failsafe/RestAssured 可以作为后续补充，但不作为第一阶段默认方案。

原因：

- `make test` 的目标是验证已启动服务，不是跑 Maven 集成测试生命周期。
- Maven 启动成本更高，容易把 smoke test 演化为完整集成测试套件。
- 后续模型 eval 更适合放在 Python 生态，而不是 Maven 测试生命周期。

## 架构设计

测试工程统一收敛在 `tests/` 目录下，分为四层：

```text
Makefile
  -> uv run python tests/smoke/run.py
    -> 发现并编排 tests/smoke/suites/*/*.hurl
      -> hurl --test <suite files>
        -> 已启动的 algo-mentor 后端 API
```

职责边界：

- `Makefile`：统一对外命令入口，只暴露用户需要记住的命令。
- `tests/smoke/run.py`：smoke runner，负责变量生成、环境读取、suite 发现、命令执行、报告目录和退出码。
- `tests/smoke/suites/*/*.hurl`：声明式 HTTP 测试流程，负责请求、capture 和响应断言。
- `tests/smoke/lib`：放 smoke runner 的共享 Python 代码，例如 Hurl 命令封装、运行上下文、报告路径和环境校验。
- `tests/evals/`：后续模型 eval 的数据集、runner 和评分逻辑，不进入 `make test` 默认路径。

设计原则：

- 所有测试代码都放在 `tests/` 下，便于统一管理。
- 每个冒烟场景保持独立文件或独立 suite，避免一个 Hurl 文件无限膨胀。
- Python runner 负责“选择跑哪些场景”，Hurl 文件负责“这个场景如何断言”。
- 第一阶段默认跑稳定、低成本的核心 suite；扩展场景通过 suite、case 或标签显式选择。

## 目录规划

```text
pyproject.toml
uv.lock
tests/
  smoke/
    run.py
    lib/
      __init__.py
      hurl.py
      context.py
      reports.py
    suites/
      core/
        auth_and_learning_plan.hurl
      practice/
        practice_session.hurl
      security/
        anonymous_access.hurl
    fixtures/
      learning_plan.json
      users.json
    reports/
      .gitkeep
  evals/
    run_eval.py
    datasets/
    scorers/
    reports/
      .gitkeep
```

说明：

- `pyproject.toml` 声明 Python 版本、开发依赖和测试工程命令。
- `uv.lock` 固定 Python 依赖版本，保证本地与 CI 一致。
- `tests/smoke` 放面向已启动服务的冒烟测试，包括 runner、共享代码、Hurl suite、fixture 和报告目录。
- `tests/smoke/suites/core` 放 `make test` 默认执行的核心冒烟流程。
- `tests/smoke/suites/practice`、`tests/smoke/suites/security` 等目录放可独立扩展的场景。
- `tests/smoke/fixtures` 放稳定输入样例，不放密钥、真实用户数据或环境专属配置。
- `tests/evals` 为后续模型评测预留，第一阶段可以只创建目录或暂不创建。

## 命令设计

第一阶段新增命令：

```make
test:
	uv run python tests/smoke/run.py
```

建议支持的环境变量：

- `SMOKE_BASE_URL`：后端服务地址，默认 `http://localhost:8080`。
- `SMOKE_REPORT_DIR`：报告输出目录，默认 `reports/smoke`。
- `SMOKE_RUN_ID`：可选固定运行 ID；未提供时由 runner 生成。
- `SMOKE_KEEP_DATA`：是否保留测试数据用于排查，默认 `false`。
- `SMOKE_SUITE`：指定运行的 suite，默认 `core`；可取 `core`、`practice`、`security` 或 `all`。
- `SMOKE_CASE`：指定运行的单个 case，通常对应 suite 下的一个 `.hurl` 文件名，不包含 `.hurl` 后缀。
- `SMOKE_TAGS`：指定运行标签，作为 suite 选择的补充过滤条件。

建议扩展命令：

```make
test-smoke:
	uv run python tests/smoke/run.py

test-smoke-all:
	SMOKE_SUITE=all uv run python tests/smoke/run.py
```

命令语义：

- `make test`：默认等价于核心 API 冒烟测试，适合本地提交前和 PR 快速验证。
- `make test-smoke`：显式运行冒烟测试，方便后续 `make test` 扩展时保持语义清晰。
- `make test-smoke-all`：运行全部已登记的 smoke suite，适合本地完整验证或 CI 手动任务。

指定场景示例：

```bash
SMOKE_SUITE=practice make test
SMOKE_SUITE=practice SMOKE_CASE=practice_session make test
SMOKE_SUITE=core,security make test
SMOKE_TAGS=critical make test
```

后续 eval 命令预留：

```make
eval:
	uv run python tests/evals/run_eval.py

eval-smoke:
	uv run python tests/evals/run_eval.py --suite smoke
```

命令语义：

- `make test`：快速、确定性、低成本的 API 与业务流程冒烟测试。
- `make eval`：模型质量评测，允许更慢、更贵、更依赖外部模型。
- `make eval-smoke`：小样本模型评测，用于快速发现明显退化。

## 第一阶段冒烟范围

第一阶段重点验证认证、CSRF、学习计划主流程和权限边界。

默认 `core` suite 建议包含：

1. 访问 `/api/auth/me` 初始化 CSRF cookie，并断言未登录状态符合预期。
2. 使用唯一 smoke 邮箱调用 `/api/auth/register` 注册用户。
3. 调用 `/api/auth/me`，断言当前用户已登录且邮箱匹配。
4. 调用 `/api/learning-plans/drafts/stream` 创建学习计划草案，并消费 SSE 到 `draft_ready`。
5. 断言草案状态为 `GENERATED`，并 capture `draftId`。
6. 调用 `/api/learning-plans/drafts/{draftId}/confirm` 确认草案。
7. 断言返回 `planId`、状态为 `ACTIVE`。
8. 调用 `/api/learning-plans/{planId}` 读回计划详情。
9. 断言详情中的目标、周期、语言或标题与创建请求一致。
10. 可选调用 `DELETE /api/learning-plans/{planId}` 清理测试计划。
11. 使用无登录态请求受保护接口，断言返回 401 或 403。

可扩展 suite：

- `practice`：若本地题库 seed 已准备好，使用计划中的题目创建 practice session，并读回 session。
- `security`：扩展认证失败、未登录访问、跨用户资源隔离等权限边界。
- `sse`：若 SSE 稳定且有无模型依赖路径，再增加最小 SSE 连接冒烟；否则留到第二阶段。
- `admin`：后续如有管理后台或导入任务接口，再独立增加。

不建议第一阶段覆盖：

- 浏览器页面渲染。
- 完整 AI 对话效果。
- 真实模型回答质量。
- 复杂多用户权限矩阵。

## 场景扩展机制

新增冒烟场景时按以下方式扩展：

1. 在 `tests/smoke/suites/{suite_name}` 下新增一个或多个 `.hurl` 文件。
2. 文件名使用业务流程命名，例如 `learning_plan_confirm.hurl`、`practice_session_messages.hurl`。
3. 需要共享输入时放入 `tests/smoke/fixtures`，由 runner 转成 Hurl 变量或临时文件。
4. 默认不自动加入 `core`，除非该流程足够稳定、快速且代表主链路。
5. 对依赖 seed、外部模型或长连接的场景使用独立 suite，避免拖慢 `make test`。

runner 的发现规则：

- 默认只运行 `tests/smoke/suites/core/**/*.hurl`。
- `SMOKE_SUITE=practice` 时只运行 `practice` suite。
- `SMOKE_SUITE=all` 时运行所有 suite，但可以跳过标记为外部依赖未满足的场景。
- 多个 suite 可以用逗号分隔，例如 `SMOKE_SUITE=core,security`。
- `SMOKE_CASE=practice_session` 时只运行匹配 `practice_session.hurl` 的 case；如同时指定 `SMOKE_SUITE`，则只在该 suite 范围内查找。
- `SMOKE_CASE` 未匹配任何文件时，runner 应快速失败并打印可用 case 列表。

复杂场景处理规则：

- 单纯 HTTP 串联和 JSON 断言继续放 Hurl。
- 需要循环、条件、重试、跨用户编排或复杂清理时，优先在 Python runner 中封装能力。
- 如果某个流程大部分逻辑都需要 Python，允许新增 Python smoke case，但仍放在 `tests/smoke` 下，并由同一个 runner 编排。

## 数据策略

测试数据应可重复执行并便于排查。

- smoke 用户邮箱使用运行 ID，例如 `smoke+{run_id}@example.test`。
- 测试请求中的目标文案包含运行 ID，方便从数据库或日志定位。
- 默认尝试清理由本次运行创建的学习计划。
- 注册用户可暂不清理，避免新增测试专用管理接口；后续如数据膨胀明显，再设计按前缀清理策略。
- `SMOKE_KEEP_DATA=true` 时保留业务数据，用于失败排查。

## 失败诊断

runner 应输出以下信息：

- base URL。
- run ID。
- Hurl 文件路径。
- 报告目录。
- 失败请求所在文件和行号。
- 关键 capture 值，例如 `draftId`、`planId`，但不输出密码、cookie、CSRF token 或完整 Authorization 头。

Hurl 失败时保留原始错误输出，Python runner 只做必要包装，不吞掉上下文。

## CI 集成

CI 中 `make test` 的前置条件：

- 服务已经启动。
- 数据库迁移已完成。
- 必要 seed 数据已准备好。
- `uv` 和 `hurl` 已安装，或由 CI 镜像预置。

CI 可分阶段：

- PR 快速验证：运行 `make backend-test`、`make frontend-test`、`make test`。
- 夜间或手动任务：运行 `make eval`。

## 模型 eval 演进

后续模型 eval 不进入 `make test` 默认链路，单独通过 `make eval` 管理。

建议方向：

- `evals/datasets/*.jsonl` 保存固定样本。
- `evals/scorers` 保存规则评分器和 LLM judge 评分器。
- `evals/run_eval.py` 负责运行样本、调用服务或模型、聚合结果并判断阈值。
- 初期可以先做规则型 eval，例如 JSON 结构完整性、工具调用参数、是否避免直接给答案。
- 稳定后再引入 LLM judge 评估解释质量、代码 review 准确性和学习计划合理性。

与 smoke 的关系：

- smoke 验证服务链路可用。
- eval 验证模型行为质量。
- 两者共享 Python 3.12 + uv 基础设施，但命令、数据集、报告和失败阈值分离。

## 风险与缓解

- Hurl 未安装：runner 在启动时检查并给出明确安装提示。
- CSRF/cookie 处理失败：第一阶段显式把认证链路作为 smoke 核心场景。
- 测试数据污染：使用唯一运行 ID，默认清理计划数据，用户数据暂按前缀可识别。
- 冒烟测试变慢：`make test` 只保留最小主流程，模型 eval 放到独立命令。
- Hurl 场景变复杂：复杂逻辑迁移到 Python runner 或 Python case，Hurl 保持声明式 HTTP 流程。

## 第一阶段验收标准

- `make test` 能在已启动服务上运行完整 API 冒烟流程。
- 测试覆盖认证、CSRF、学习计划创建、确认、读回和未登录保护。
- 失败时能定位到具体请求和断言。
- Python 使用 3.12，依赖通过 `uv` 管理。
- 不依赖真实模型质量，不启动服务，不执行构建。
