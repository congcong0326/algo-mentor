MAVEN := mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository
NPM := npm --cache ./.npm --prefix frontend
COMPOSE := docker compose -f deploy/docker/docker-compose.yml
PROBLEM_SOURCE_REPO := https://github.com/fishjar/leetcode-problemset
PROBLEM_SOURCE_DIR := data/sources/leetcode-problemset
PROBLEM_SEED_DIR := data/seed
DB_SEED_URL := jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:algo_mentor}
DB_SEED_USER := ${POSTGRES_USER:algo_mentor}
DB_SEED_PASSWORD := ${POSTGRES_PASSWORD:algo_mentor_dev}
STATIC_DIR := backend/mentor-api/src/main/resources/static

.PHONY: build package up down backend-build backend-test backend-dev frontend-install frontend-build frontend-test frontend-dev sync-frontend problem-source problem-seed db-seed clean

build: backend-build frontend-build

package: frontend-build sync-frontend backend-build

up: package
	$(COMPOSE) up -d --build

down:
	$(COMPOSE) down

backend-build:
	$(MAVEN) package

backend-test:
	$(MAVEN) test

backend-dev:
	$(MAVEN) -pl mentor-api -am spring-boot:run

frontend-install:
	$(NPM) install

frontend-build:
	$(NPM) run build

frontend-test:
	$(NPM) test

frontend-dev:
	$(NPM) run dev -- --host 0.0.0.0

problem-source:
	@if [ -d "$(PROBLEM_SOURCE_DIR)/.git" ]; then \
		git -C "$(PROBLEM_SOURCE_DIR)" pull --ff-only; \
	else \
		mkdir -p data/sources; \
		git clone "$(PROBLEM_SOURCE_REPO)" "$(PROBLEM_SOURCE_DIR)"; \
	fi

problem-seed:
	python3 -m tools.problem_seed.prepare_seed --source-dir "$(PROBLEM_SOURCE_DIR)" --output-dir "$(PROBLEM_SEED_DIR)"

db-seed:
	$(MAVEN) -pl mentor-api -am -DskipTests spring-boot:run \
		-Dspring-boot.run.arguments="--algo-mentor.problem.seed.enabled=true --algo-mentor.problem.seed.path=$(PROBLEM_SEED_DIR) --spring.datasource.url=$(DB_SEED_URL) --spring.datasource.username=$(DB_SEED_USER) --spring.datasource.password=$(DB_SEED_PASSWORD) --spring.flyway.enabled=true" \
		-Dspring-boot.run.profiles=local

sync-frontend:
	rm -rf $(STATIC_DIR)
	mkdir -p $(STATIC_DIR)
	cp -R frontend/dist/. $(STATIC_DIR)/

clean:
	$(MAVEN) clean
	rm -rf frontend/dist frontend/build
