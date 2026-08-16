# 预发布快速发布

## 目标与边界

`make deploy-preprod-fast` 用于将**已提交且不包含 Flyway 迁移**的应用版本发布到 `leetmentor-dev` 预发布环境。它适用于前端界面修改和不改变数据库 schema 或数据的后端代码修改。

该命令不发布到生产环境，也不会修改 PostgreSQL、Redis、目标机运行时配置、密钥或网络设施。发现任一 Flyway 迁移文件有变化时会拒绝执行；这类版本必须走数据库感知的完整发布流程。

应用容器在 `preprod` profile 下启动时仍会运行 Flyway 校验。快速发布只是确认本次版本没有新的迁移需要执行，并不关闭 schema 校验。

## 配置分层

| 位置 | 职责 | 是否被打包进 JAR |
| --- | --- | --- |
| `backend/mentor-api/src/main/resources/application.yml` | 跨环境默认配置及 Spring 环境变量映射 | 是 |
| `backend/mentor-api/src/main/resources/application-preprod.yml` | 预发布 profile 的数据库、缓存、Streams 等配置绑定 | 是 |
| `deploy/docker/preprod-runtime-env.required` | 预发布必须存在的环境变量键名及所属环境文件，不含值 | 随发布制品上传，仅用于校验 |
| `/etc/algo-mentor/database.env`、`redis.env`、`runtime.env` | 目标机真实连接信息、密钥、开关和环境专属参数 | 否，始终保留在目标机 |

因此，新增 Spring Boot 参数时，变量映射应该写入 `application.yml`；只对预发布生效的绑定放入 `application-preprod.yml`。真实值绝不写入仓库或镜像：数据库凭据在 `database.env`，Redis 凭据在 `redis.env`，非敏感功能开关和其他运行时参数在 `runtime.env`。

例如，提交历史 Tool 的两个开关已在 `application.yml` 中提供映射；预发布值必须位于目标机 `/etc/algo-mentor/runtime.env`：

```dotenv
PRACTICE_CHAT_SUBMISSION_HISTORY_TOOL_ENABLED=true
PRACTICE_CHAT_SUBMISSION_HISTORY_CODE_DETAIL_ENABLED=true
```

它们同时登记在 `preprod-runtime-env.required`。快速发布会仅检查键和值是否存在，不读取或打印真实值。即使配置文件提供默认值，预发布的功能开关也应显式登记和设置，以防后续默认值变更造成行为漂移。

## 新增配置的发布清单

新增配置项时，先完成以下事项，再运行快速发布：

1. 在应用配置中声明环境变量映射及适当默认值；敏感值不得有可用默认值。
2. 对预发布必需值或要显式固定的开关，在 `deploy/docker/preprod-runtime-env.required` 增加 `<文件名>:<变量名>`。
3. 通过受控目标机操作将真实值写入 `/etc/algo-mentor/` 对应文件，并保持现有 `root:algo-mentor`、`0640` 权限。不要把该文件复制回仓库。
4. 提交应用配置和环境变量契约；快速发布会在替换容器前校验目标机是否已具备该键。

若第 3 步遗漏，命令会在旧容器仍运行时失败，并指出缺少的文件名和变量名。

## 使用方式

首次使用时，当前运行中的旧容器没有发布提交标签，需要明确提供它对应的已部署提交：

```bash
make deploy-preprod-fast PREPROD_BASE_REF=ab91550
```

成功后，容器会带有提交标签，后续正常发布不需要再传入基线：

```bash
make deploy-preprod-fast
```

可通过 `PREPROD_HOST` 和 `PREPROD_CONTAINER_NAME` 覆盖默认目标，但只能用于具有相同目录、受保护环境文件与 Docker 运行约定的预发布主机。

## 发布行为与回滚

命令要求应用发布输入（后端、前端、构建文件、预发布 Dockerfile 和发布脚本）没有未提交变更，且发布 ref 已提交；无关的本地文档或运维记录不阻塞发布。根据变更范围运行前端或后端测试，再构建包含前端静态文件的应用 JAR。制品带 SHA-256 上传到目标机的独立 release 目录，目标机构建新的运行镜像。

旧容器被停止并按带 release ID 的名称保留。新容器通过 readiness 健康检查后才视为成功；若启动、运行或健康检查在 60 秒内失败，脚本会移除新容器并恢复旧容器。快速发布不含数据库迁移，故容器级回滚不需要回退 schema。
