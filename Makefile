ifneq (,$(wildcard .env))
include .env
export
endif

MAVEN := mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository
NPM := npm --cache ./.npm --prefix frontend
COMPOSE := docker compose -f deploy/docker/docker-compose.yml
PROXY_PORT ?= 8080
API_PORT ?= 18080
SERVER_PORT ?= $(API_PORT)
PROMETHEUS_HOST ?= localhost
PROMETHEUS_PORT ?= 9090
OBSERVABILITY_PROFILE := --profile observability
OBSERVABILITY_CHECK := deploy/docker/observability/check-docker.sh
POSTGRES_VERSION ?= 16
POSTGRES_HOST ?= localhost
POSTGRES_PORT ?= 5432
POSTGRES_DB ?= algo_mentor
POSTGRES_USER ?= algo_mentor
POSTGRES_PASSWORD ?= algo_mentor_dev
PROBLEM_SOURCE_REPO := https://github.com/fishjar/leetcode-problemset
PROBLEM_SOURCE_DIR := data/sources/leetcode-problemset
PROBLEM_SEED_DIR := data/seed
PROBLEM_SEED_ABS_DIR := $(abspath $(PROBLEM_SEED_DIR))
DB_SEED_URL := jdbc:postgresql://$(POSTGRES_HOST):$(POSTGRES_PORT)/$(POSTGRES_DB)
DB_SEED_USER := $(POSTGRES_USER)
DB_SEED_PASSWORD := $(POSTGRES_PASSWORD)
STATIC_DIR := backend/mentor-api/src/main/resources/static

.PHONY: build package package-skip-tests up down proxy-up proxy-down proxy-restart proxy-status observability-up observability-down observability-status observability-logs observability-check backend-build backend-build-skip-tests backend-test backend-dev frontend-install frontend-build frontend-test frontend-dev test test-smoke test-smoke-all test-env sync-frontend problem-source problem-seed db-install db-seed clean

build: backend-build frontend-build

package: frontend-build sync-frontend backend-build

package-skip-tests: frontend-build sync-frontend backend-build-skip-tests

up: package-skip-tests
	@if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1 && docker info >/dev/null 2>&1; then \
		$(COMPOSE) up -d --build; \
	else \
		if ! command -v psql >/dev/null 2>&1; then \
			echo "psql is not installed. Run 'make db-install' once before 'make up' in a development container." >&2; \
			exit 1; \
		fi; \
		if ! PGPASSWORD="$(POSTGRES_PASSWORD)" psql -h "$(POSTGRES_HOST)" -p "$(POSTGRES_PORT)" -U "$(POSTGRES_USER)" -d "$(POSTGRES_DB)" -v ON_ERROR_STOP=1 -c "SELECT 1;" >/dev/null 2>&1; then \
			echo "Cannot connect to PostgreSQL at $(POSTGRES_HOST):$(POSTGRES_PORT)/$(POSTGRES_DB). Run 'make db-install' once or start the local database." >&2; \
			exit 1; \
		fi; \
		api_jar="$$(find backend/mentor-api/target -maxdepth 1 -type f -name 'mentor-api-*.jar' ! -name '*.original' | sort | tail -n 1)"; \
		if [ -z "$$api_jar" ]; then \
			echo "Cannot find packaged mentor-api jar under backend/mentor-api/target." >&2; \
			exit 1; \
		fi; \
		API_PORT="$(API_PORT)" PROXY_PORT="$(PROXY_PORT)" PROMETHEUS_HOST="$(PROMETHEUS_HOST)" PROMETHEUS_PORT="$(PROMETHEUS_PORT)" scripts/local-proxy.sh start; \
		echo "Docker is unavailable. Starting $$api_jar on :$(API_PORT) behind proxy :$(PROXY_PORT) with PostgreSQL at $(POSTGRES_HOST):$(POSTGRES_PORT)."; \
		exec env \
			API_PORT="$(API_PORT)" \
			SERVER_PORT="$(API_PORT)" \
			SPRING_PROFILES_ACTIVE=local \
			POSTGRES_HOST="$(POSTGRES_HOST)" \
			POSTGRES_PORT="$(POSTGRES_PORT)" \
			POSTGRES_DB="$(POSTGRES_DB)" \
			POSTGRES_USER="$(POSTGRES_USER)" \
			POSTGRES_PASSWORD="$(POSTGRES_PASSWORD)" \
			java -jar "$$api_jar"; \
	fi

down:
	@if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1 && docker info >/dev/null 2>&1; then \
		$(COMPOSE) down; \
	else \
		API_PORT="$(API_PORT)" PROXY_PORT="$(PROXY_PORT)" PROMETHEUS_HOST="$(PROMETHEUS_HOST)" PROMETHEUS_PORT="$(PROMETHEUS_PORT)" scripts/local-proxy.sh stop; \
	fi

proxy-up:
	API_PORT="$(API_PORT)" PROXY_PORT="$(PROXY_PORT)" PROMETHEUS_HOST="$(PROMETHEUS_HOST)" PROMETHEUS_PORT="$(PROMETHEUS_PORT)" scripts/local-proxy.sh start

proxy-down:
	API_PORT="$(API_PORT)" PROXY_PORT="$(PROXY_PORT)" PROMETHEUS_HOST="$(PROMETHEUS_HOST)" PROMETHEUS_PORT="$(PROMETHEUS_PORT)" scripts/local-proxy.sh stop

proxy-restart:
	API_PORT="$(API_PORT)" PROXY_PORT="$(PROXY_PORT)" PROMETHEUS_HOST="$(PROMETHEUS_HOST)" PROMETHEUS_PORT="$(PROMETHEUS_PORT)" scripts/local-proxy.sh restart

proxy-status:
	API_PORT="$(API_PORT)" PROXY_PORT="$(PROXY_PORT)" PROMETHEUS_HOST="$(PROMETHEUS_HOST)" PROMETHEUS_PORT="$(PROMETHEUS_PORT)" scripts/local-proxy.sh status

observability-check:
	$(OBSERVABILITY_CHECK)

observability-up: observability-check
	$(COMPOSE) $(OBSERVABILITY_PROFILE) up -d prometheus grafana loki promtail
	@echo "Prometheus: http://localhost:$${PROMETHEUS_PORT:-9090}"
	@echo "Grafana:    http://localhost:$${GRAFANA_PORT:-3000}"
	@echo "Loki:       http://localhost:$${LOKI_PORT:-3100}"

observability-down: observability-check
	$(COMPOSE) $(OBSERVABILITY_PROFILE) stop prometheus grafana loki promtail
	$(COMPOSE) $(OBSERVABILITY_PROFILE) rm -f prometheus grafana loki promtail

observability-status: observability-check
	$(COMPOSE) $(OBSERVABILITY_PROFILE) ps prometheus grafana loki promtail
	@echo "Prometheus readiness: curl http://localhost:$${PROMETHEUS_PORT:-9090}/-/ready"
	@echo "Grafana health:       curl http://localhost:$${GRAFANA_PORT:-3000}/api/health"
	@echo "Loki ready:           curl http://localhost:$${LOKI_PORT:-3100}/ready"

observability-logs: observability-check
	$(COMPOSE) $(OBSERVABILITY_PROFILE) logs -f --tail=200 prometheus grafana loki promtail

backend-build:
	$(MAVEN) package

backend-build-skip-tests:
	$(MAVEN) -DskipTests package

backend-test:
	$(MAVEN) test

backend-dev:
	API_PORT="$(API_PORT)" SERVER_PORT="$(API_PORT)" $(MAVEN) -pl mentor-api -am spring-boot:run

frontend-install:
	$(NPM) install

frontend-build:
	$(NPM) run build

frontend-test:
	$(NPM) test

frontend-dev:
	$(NPM) run dev -- --host 0.0.0.0

test: test-smoke

test-smoke:
	uv run python tests/smoke/run.py

test-smoke-all:
	SMOKE_SUITE=all uv run python tests/smoke/run.py

test-env:
	uv run python --version
	@command -v hurl >/dev/null 2>&1 || { echo "hurl is not installed. Install Hurl before running smoke tests." >&2; exit 1; }
	hurl --version

problem-source:
	@if [ -d "$(PROBLEM_SOURCE_DIR)/.git" ]; then \
		git -C "$(PROBLEM_SOURCE_DIR)" pull --ff-only; \
	else \
		mkdir -p data/sources; \
		git clone "$(PROBLEM_SOURCE_REPO)" "$(PROBLEM_SOURCE_DIR)"; \
	fi

problem-seed:
	python3 -m tools.problem_seed.prepare_seed --source-dir "$(PROBLEM_SOURCE_DIR)" --output-dir "$(PROBLEM_SEED_DIR)"

db-install:
	@if [ "$$(id -u)" -ne 0 ]; then \
		echo "db-install requires root in the development container." >&2; \
		exit 1; \
	fi; \
	if [ ! -r /etc/os-release ]; then \
		echo "Cannot detect OS: /etc/os-release is missing." >&2; \
		exit 1; \
	fi; \
	. /etc/os-release; \
	if ! command -v apt-get >/dev/null 2>&1; then \
		echo "db-install currently supports Debian/Ubuntu containers with apt-get only." >&2; \
		exit 1; \
	fi; \
	if [ "$${ID:-}" != "debian" ] && [ "$${ID:-}" != "ubuntu" ] && ! printf '%s\n' "$${ID_LIKE:-}" | grep -Eq '(^| )debian( |$$)'; then \
		echo "db-install currently supports Debian/Ubuntu containers only." >&2; \
		exit 1; \
	fi; \
	set -eu; \
	export DEBIAN_FRONTEND=noninteractive; \
	apt-get update; \
	if ! apt-cache policy "postgresql-$(POSTGRES_VERSION)" | awk '/Candidate:/ && $$2 != "(none)" { found = 1 } END { exit !found }'; then \
		apt-get install -y ca-certificates curl gnupg; \
		codename="$${VERSION_CODENAME:-$${UBUNTU_CODENAME:-}}"; \
		if [ -z "$$codename" ]; then \
			echo "Cannot detect Debian/Ubuntu codename for PostgreSQL apt repository." >&2; \
			exit 1; \
		fi; \
		install -d -m 0755 /usr/share/postgresql-common/pgdg; \
		rm -f /usr/share/postgresql-common/pgdg/apt.postgresql.org.gpg; \
		curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | gpg --dearmor -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.gpg; \
		echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.gpg] https://apt.postgresql.org/pub/repos/apt $$codename-pgdg main" > /etc/apt/sources.list.d/pgdg.list; \
		apt-get update; \
	fi; \
	apt-get install -y "postgresql-$(POSTGRES_VERSION)" "postgresql-client-$(POSTGRES_VERSION)"; \
	if command -v pg_lsclusters >/dev/null 2>&1 && ! pg_lsclusters | awk '$$1 == "$(POSTGRES_VERSION)" && $$2 == "main" { found = 1 } END { exit !found }'; then \
		pg_createcluster "$(POSTGRES_VERSION)" main; \
	fi; \
	postgresql_conf="/etc/postgresql/$(POSTGRES_VERSION)/main/postgresql.conf"; \
	if [ -f "$$postgresql_conf" ]; then \
		sed -i "s/^#\?port = .*/port = $(POSTGRES_PORT)/" "$$postgresql_conf"; \
		sed -i "s/^#\?listen_addresses = .*/listen_addresses = 'localhost'/" "$$postgresql_conf"; \
	fi; \
	if command -v pg_ctlcluster >/dev/null 2>&1; then \
		if pg_ctlcluster "$(POSTGRES_VERSION)" main status >/dev/null 2>&1; then \
			pg_ctlcluster "$(POSTGRES_VERSION)" main restart; \
		else \
			pg_ctlcluster "$(POSTGRES_VERSION)" main start; \
		fi; \
	else \
		service postgresql start; \
	fi; \
	sql_literal() { printf '%s' "$$1" | sed "s/'/''/g"; }; \
	sql_identifier() { printf '%s' "$$1" | sed 's/"/""/g'; }; \
	app_user_lit="$$(sql_literal "$(POSTGRES_USER)")"; \
	app_user_ident="$$(sql_identifier "$(POSTGRES_USER)")"; \
	app_password_lit="$$(sql_literal "$(POSTGRES_PASSWORD)")"; \
	app_db_lit="$$(sql_literal "$(POSTGRES_DB)")"; \
	app_db_ident="$$(sql_identifier "$(POSTGRES_DB)")"; \
	if ! runuser -u postgres -- psql -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$$app_user_lit'" | grep -q 1; then \
		runuser -u postgres -- psql -v ON_ERROR_STOP=1 -c "CREATE ROLE \"$$app_user_ident\" LOGIN PASSWORD '$$app_password_lit'"; \
	else \
		runuser -u postgres -- psql -v ON_ERROR_STOP=1 -c "ALTER ROLE \"$$app_user_ident\" WITH LOGIN PASSWORD '$$app_password_lit'"; \
	fi; \
	if ! runuser -u postgres -- psql -tAc "SELECT 1 FROM pg_database WHERE datname = '$$app_db_lit'" | grep -q 1; then \
		runuser -u postgres -- psql -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"$$app_db_ident\" OWNER \"$$app_user_ident\""; \
	else \
		runuser -u postgres -- psql -v ON_ERROR_STOP=1 -c "ALTER DATABASE \"$$app_db_ident\" OWNER TO \"$$app_user_ident\""; \
	fi; \
	runuser -u postgres -- psql -v ON_ERROR_STOP=1 -d "$(POSTGRES_DB)" -c "ALTER SCHEMA public OWNER TO \"$$app_user_ident\"; GRANT ALL ON SCHEMA public TO \"$$app_user_ident\";" >/dev/null; \
	PGPASSWORD="$(POSTGRES_PASSWORD)" psql -h localhost -p "$(POSTGRES_PORT)" -U "$(POSTGRES_USER)" -d "$(POSTGRES_DB)" -v ON_ERROR_STOP=1 -c "SELECT 1;" >/dev/null; \
	echo "PostgreSQL $(POSTGRES_VERSION) is ready at localhost:$(POSTGRES_PORT)/$(POSTGRES_DB)."

db-seed:
	API_PORT="$(API_PORT)" SERVER_PORT="$(API_PORT)" $(MAVEN) -pl mentor-api -am -DskipTests spring-boot:run \
		-Dspring-boot.run.arguments="--algo-mentor.problem.seed.enabled=true --algo-mentor.problem.seed.path=$(PROBLEM_SEED_ABS_DIR) --spring.datasource.url=$(DB_SEED_URL) --spring.datasource.username=$(DB_SEED_USER) --spring.datasource.password=$(DB_SEED_PASSWORD) --spring.flyway.enabled=true" \
		-Dspring-boot.run.profiles=local

sync-frontend:
	rm -rf $(STATIC_DIR)
	mkdir -p $(STATIC_DIR)
	cp -R frontend/dist/. $(STATIC_DIR)/

clean:
	$(MAVEN) clean
	rm -rf frontend/dist frontend/build
