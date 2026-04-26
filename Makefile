.PHONY: test-int test-int-up test-int-down test-int-clean test-int-build java-test

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

# Build the application jar
test-int-build:
	./gradlew bootJar

# Start the services
test-int-up:
	docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --build postgres backend
	@echo "Waiting for backend to be healthy..."
	@n=0; until [ $$(docker inspect --format='{{.State.Health.Status}}' budget-tracker-backend) = 'healthy' ] || [ $$n -ge 30 ]; do sleep 2; n=$$(($$n + 1)); done; \
	if [ $$n -ge 30 ]; then \
	  echo "Error: Backend failed to become healthy after 60 seconds"; \
	  docker compose -f docker-compose.yml -f docker-compose.test.yml stop; \
	  exit 1; \
	fi
	@if [ $$(docker inspect --format='{{.State.Running}}' budget-tracker-backend) != 'true' ]; then \
	  echo "Error: Backend container is not running"; \
	  docker compose -f docker-compose.yml -f docker-compose.test.yml stop; \
	  exit 1; \
	fi
	@echo "Backend is healthy and running."

# Run the integration tests using Gradle
test-int-run:
	./gradlew test --include-tags 'integration'

# Stop the services
test-int-down:
	docker compose -f docker-compose.yml -f docker-compose.test.yml stop backend postgres
	@echo "Services stopped."

# Clean up everything
test-int-clean: test-int-down
	docker compose -f docker-compose.yml -f docker-compose.test.yml down -v
	@echo "Cleanup completed."

# Run local tests
java-test:
	./gradlew test