# 个人自部署镜像：在容器内完成前端构建、后端打包，运行镜像同时携带初始化所需的种子数据。
# 构建上下文为仓库根目录，忽略规则见同目录 Dockerfile.dockerignore。

# ---------- 前端构建 ----------
FROM node:22-bookworm-slim AS frontend
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json frontend/
RUN --mount=type=cache,target=/root/.npm \
    npm --prefix frontend ci --no-audit --no-fund
COPY frontend frontend
# SEO 预渲染脚本会读取题库种子数据。
COPY data/seed data/seed
COPY data/problem-insight-seed data/problem-insight-seed
RUN npm --prefix frontend run build

# ---------- 后端打包 ----------
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /src
COPY backend backend
COPY --from=frontend /src/frontend/dist/ backend/mentor-api/src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -f backend/pom.xml -B -ntp -DskipTests package \
    && cp "$(find backend/mentor-api/target -maxdepth 1 -name 'mentor-api-*.jar' ! -name '*.original' | head -n 1)" /src/app.jar

# ---------- 运行镜像 ----------
FROM eclipse-temurin:17-jre

# postgresql-client：初始化脚本判断是否已导入数据，同时供应用内数据库备份功能使用。
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client \
    && rm -rf /var/lib/apt/lists/*

ARG APP_UID=10001
ARG APP_GID=10001
RUN groupadd --system --gid "${APP_GID}" algo-mentor \
    && useradd --system --uid "${APP_UID}" --gid "${APP_GID}" --home-dir /app --no-create-home algo-mentor

WORKDIR /app
COPY --from=backend /src/app.jar /app/app.jar
COPY data/seed /app/data/seed
COPY data/problem-insight-seed /app/data/problem-insight-seed
COPY data/company-seed /app/data/company-seed
COPY data/problem-metadata-seed /app/data/problem-metadata-seed
# 元数据 manifest 以 ../sources/... 相对路径引用该报告并校验 SHA256。
COPY data/sources/leetcode-api-metadata/problem_metadata_fetch_report.json /app/data/sources/leetcode-api-metadata/problem_metadata_fetch_report.json
COPY data/learning-plan-template-seed /app/data/learning-plan-template-seed
COPY knowledge-base /app/knowledge-base
COPY deploy/selfhost/init-data.sh /app/init-data.sh
RUN chmod 0755 /app/init-data.sh && mkdir -p /app/logs && chown algo-mentor:algo-mentor /app/logs

USER algo-mentor
EXPOSE 18080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
