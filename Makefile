IMAGE_NAME := conduktor_quick_start_in_a_single_container
CONTAINER_NAME := conduktor_quick_start_in_a_single_container
CERTS_DIR := ./certs

# Default target
all: rm build run setup

# Build the Docker image and Java SDK
build:
	@echo "Building Java SDK..."
	cd java_sdk && mvn compile -q
	@echo "Building Docker image..."
	docker build . -t $(IMAGE_NAME)

# Run the container
run:
	@echo "Starting container..."
	docker run -d --name $(CONTAINER_NAME) \
		-p 8080:8080 \
		-p 8888:8888 \
		-p 6969:6969 \
		-v $(PWD)/certs/:/var/lib/conduktor/certs \
		$(IMAGE_NAME)
	@echo "Container started. Waiting for services..."
	@printf "Waiting for SSL certificates generation"
	@while [ ! -f $(CERTS_DIR)/.certs-complete ]; do sleep 1; printf "."; done
	@echo 
	@printf "Waiting for Console to be ready"
	@while ! curl -sf http://localhost:8080/platform/api/modules/resources/health/live >/dev/null 2>&1; do sleep 1; printf "."; done
	@echo 
	@printf "Waiting for Gateway to be ready"
	@while ! curl -sf http://localhost:8888/health/ready >/dev/null 2>&1; do sleep 1; printf "."; done
	@echo 
	@echo "All services are up and running!"

# Stop the container
stop:
	@echo "Stopping container..."
	-docker stop $(CONTAINER_NAME) 2>/dev/null || true

# Remove the container
rm: stop
	@echo "Removing container..."
	-docker rm $(CONTAINER_NAME) 2>/dev/null || true
	@rm -rf $(CERTS_DIR)

# Clean everything (container + image + Java SDK)
clean: rm
	@echo "Removing image..."
	-docker rmi $(IMAGE_NAME) 2>/dev/null || true
	@echo "Cleaning Java SDK..."
	cd java_sdk && mvn clean -q
	@echo "Removing local certs..."
	-rm -rf $(CERTS_DIR) 2>/dev/null || true
	-rm -f *.properties 2>/dev/null || true

# View logs
logs:
	docker logs -f $(CONTAINER_NAME)

# View setup logs specifically
setup-logs:
	docker exec $(CONTAINER_NAME) cat /var/log/conduktor/setup.log

setup:
	./setup_gateway.sh

# Help
help:
	@echo "Conduktor Quick Start Makefile"
	@echo ""
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@echo "  all         - Remove, build, run, and setup (default)"
	@echo "  build       - Build Java SDK and Docker image"
	@echo "  run         - Run container and wait for services"
	@echo "  stop        - Stop container"
	@echo "  rm          - Stop and remove container"
	@echo "  clean       - Remove container, image, Java SDK target, and local certs"
	@echo "  logs        - Follow container logs"
	@echo "  setup-logs  - View setup script logs"
	@echo "  help        - Show this help"
