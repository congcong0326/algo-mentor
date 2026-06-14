MAVEN := mvn -f backend/pom.xml -B -ntp -Dmaven.repo.local=./.m2/repository
NPM := npm --cache ./.npm --prefix frontend
COMPOSE := docker compose -f deploy/docker/docker-compose.yml
STATIC_DIR := backend/mentor-api/src/main/resources/static

.PHONY: build package up down backend-build backend-test backend-dev frontend-install frontend-build frontend-test frontend-dev sync-frontend clean

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

sync-frontend:
	rm -rf $(STATIC_DIR)
	mkdir -p $(STATIC_DIR)
	cp -R frontend/dist/. $(STATIC_DIR)/

clean:
	$(MAVEN) clean
	rm -rf frontend/dist frontend/build
