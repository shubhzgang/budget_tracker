SHELL := /bin/bash

.PHONY: test-int test-int-up test-int-down test-int-clean test-int-build java-test test-e2e

# Main target: Run the full integration test suite
test-int: test-int-build test-int-up
	@$(MAKE) test-int-run; \
	EXIT_CODE=$$?; \
	$(MAKE) test-int-clean; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "Integration tests completed successfully."; \
	else \
		echo "Integration	tests failed."; \
	fi; \
	exit $$EXIT_CODE

# Verify Java 21 is installed
check-java:
	@if ! command -v java >/dev/null 2>&1; then \
		echo "Error: java is not installed or not in PATH."; \
		echo "This project requires Java 21. Install it."; \
		exit 1; \
	fi
	@JAVA_VER=$$(java -version 2>&1 | head -1 | cut -d'"' -f2); \
	if [[ "$$JAVA_VER" != 21* ]]; then \
		echo "Error: Java version $$JAVA_VER detected."; \
		echo "This project requires Java 21."; \
		exit 1; \
	fi

# Build the application jar locally
build: check-java
	./gradlew bootJar -x test

# Build the application jar (alias for test-int compatibility)
test-int-build: build

# Start the services
test-int-up:
	@-docker volume rm budget_tracker_pgdata_test_int 2>/dev/null || true
	docker compose -f docker-compose.yml -f docker-compose.test-int.yml up -d --build postgres backend
	@echo "Waiting for backend to be healthy..."
	@n=0; until [ $$(docker inspect --format='{{.State.Health.Status}}' budget-tracker-backend) = 'healthy' ] || [ $$n -ge 30 ]; do sleep 2; n=$$(($$n + 1)); done; \
	if [ $$n -ge 30 ]; then \
	  echo "Error: Backend failed to become healthy after 60 seconds"; \
	  docker compose -f docker-compose.yml -f docker-compose.test-int.yml stop; \
	  exit 1; \
	fi
	@if [ $$(docker inspect --format='{{.State.Running}}' budget-tracker-backend) != 'true' ]; then \
	  echo "Error: Backend container is not running"; \
	  docker compose -f docker-compose.yml -f docker-compose.test-int.yml stop; \
	  exit 1; \
	fi
	@echo "Backend is healthy and running."

# Run the integration tests using Gradle
test-int-run:
	./gradlew test -Pintegration

# Stop the services
test-int-down:
	docker compose -f docker-compose.yml -f docker-compose.test-int.yml stop backend postgres
	@echo "Services stopped."

# Clean up everything
test-int-clean: test-int-down
	@-docker volume rm budget_tracker_pgdata_test_int 2>/dev/null || true
	docker compose -f docker-compose.yml -f docker-compose.test-int.yml down
	@echo "Cleanup completed."

# Run local tests
java-test:
	./gradlew test

# Detect OS for Playwright install flags
OS_NAME := $(shell uname -s)

# Main target: Run the full E2E test suite
test-e2e: build build-frontend
	@echo "Starting full stack for E2E tests..."
	@-docker volume rm budget_tracker_pgdata_test_e2e 2>/dev/null || true
	docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build
	@echo "Waiting for frontend and backend to be healthy..."
	@n=0; until [ $$(docker inspect --format='{{.State.Health.Status}}' budget-tracker-backend) = 'healthy' ] || [ $$n -ge 30 ]; do sleep 2; n=$$(($$n + 1)); done; \
	if [ $$n -ge 30 ]; then \
	  echo "Error: Backend failed to become healthy"; \
	  docker compose down; \
	  exit 1; \
	fi
	@echo "Stack is ready. Running Playwright tests on $(OS_NAME)..."
	@if [ "$(OS_NAME)" = "Linux" ]; then \
		DISTRO=$$(. /etc/os-release && echo $${ID:-""}); \
		if echo "$${DISTRO}" | grep -qE "^(debian|ubuntu)$$"; then \
			PLAYWRIGHT_INSTALL_CMD="npx playwright install chromium --with-deps"; \
		else \
			PLAYWRIGHT_INSTALL_CMD="npx playwright install chromium"; \
		fi; \
	else \
		PLAYWRIGHT_INSTALL_CMD="npx playwright install chromium"; \
	fi; \
	cd e2e && npm install && $${PLAYWRIGHT_INSTALL_CMD} && npm run test; \
	EXIT_CODE=$$?; \
	cd ..; \
	docker volume rm budget_tracker_pgdata_test_e2e 2>/dev/null || true; \
	docker compose -f docker-compose.yml -f docker-compose.e2e.yml down; \
	exit $$EXIT_CODE

# Launch the entire stack for local use
run-stack: build build-frontend
	@echo "Launching Budget Tracker stack..."
	docker compose up --build -d

# Stop the stack and remove volumes
stop-stack:
	@echo "Stopping stack (keeping volumes)..."
	docker compose down

# Launch the stack with a pre-seeded test account
run-demo: build
	@echo "Launching Budget Tracker in DEMO mode (test@example.com / password)..."
	@-docker volume rm budget_tracker_pgdata_demo 2>/dev/null || true
	docker compose -f docker-compose.yml -f docker-compose.demo.yml down
	docker compose -f docker-compose.yml -f docker-compose.demo.yml up --build -d