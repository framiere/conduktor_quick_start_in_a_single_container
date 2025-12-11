IMAGE_NAME := conduktor_quick_start_in_a_single_container
CONTAINER_NAME := conduktor_quick_start_in_a_single_container
CERTS_DIR := ./certs

# Default target
all: rm build run certs setup

# Build the Docker image
build:
	@echo "Building Docker image..."
	docker build . -t $(IMAGE_NAME)

# Run the container
run:
	@echo "Starting container..."
	docker run -d --name $(CONTAINER_NAME) \
		-p 8080:8080 \
		-p 8888:8888 \
		-p 6969:6969 \
		$(IMAGE_NAME)
	@echo "Container started. Waiting for services..."

# Stop the container
stop:
	@echo "Stopping container..."
	-docker stop $(CONTAINER_NAME) 2>/dev/null || true

# Remove the container
rm: stop
	@echo "Removing container..."
	-docker rm $(CONTAINER_NAME) 2>/dev/null || true

# Clean everything (container + image)
clean: rm
	@echo "Removing image..."
	-docker rmi $(IMAGE_NAME) 2>/dev/null || true
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

# Copy certificates from container
certs:
	@echo "Copying certificates from container..."
	@mkdir -p $(CERTS_DIR)
	docker cp $(CONTAINER_NAME):/var/lib/conduktor/certs/ca.crt $(CERTS_DIR)/
	docker cp $(CONTAINER_NAME):/var/lib/conduktor/certs/user1.keystore.jks $(CERTS_DIR)/
	docker cp $(CONTAINER_NAME):/var/lib/conduktor/certs/user1.truststore.jks $(CERTS_DIR)/
	@echo ""
	@echo "Creating properties files..."
	@echo "security.protocol=SSL" > admin.properties
	@echo "ssl.truststore.location=$(CERTS_DIR)/admin.truststore.jks" >> admin.properties
	@echo "ssl.truststore.password=conduktor" >> admin.properties
	@echo "ssl.keystore.location=$(CERTS_DIR)/admin.keystore.jks" >> admin.properties
	@echo "ssl.keystore.password=conduktor" >> admin.properties
	@echo "ssl.key.password=conduktor" >> admin.properties
	@echo ""
	@echo "security.protocol=SSL" > user1.properties
	@echo "ssl.truststore.location=$(CERTS_DIR)/user1.truststore.jks" >> user1.properties
	@echo "ssl.truststore.password=conduktor" >> user1.properties
	@echo "ssl.keystore.location=$(CERTS_DIR)/user1.keystore.jks" >> user1.properties
	@echo "ssl.keystore.password=conduktor" >> user1.properties
	@echo "ssl.key.password=conduktor" >> user1.properties
	@echo ""
	@echo "Certificates copied to $(CERTS_DIR)/"
	@echo "Properties files created: admin.properties, user1.properties"
	@ls -la $(CERTS_DIR)/

# Show status
status:
	@echo "=============================================="
	@echo "  Conduktor Quick Start (mTLS)"
	@echo "=============================================="
	@echo ""
	@echo "Console:     http://localhost:8080"
	@echo "  Login:     admin@demo.dev"
	@echo "  Password:  123_ABC_abc"
	@echo ""
	@echo "Gateway API: http://localhost:8888"
	@echo "  Username:  admin"
	@echo "  Password:  conduktor"
	@echo ""
	@echo "Gateway Kafka (mTLS): localhost:6969"
	@echo ""
	@echo "Virtual Clusters:"
	@echo "  - demo       (ACL disabled)"
	@echo "  - demo-acl   (ACL enabled)"
	@echo ""
	@echo "To get certificates for local use:"
	@echo "  make certs"
	@echo ""
	@echo "To test with kafka-topics:"
	@echo "  make certs"
	@echo "  kafka-topics --bootstrap-server localhost:6969 \\"
	@echo "    --command-config admin.properties --list"
	@echo ""
	@echo "=============================================="

# Test mTLS connection
test:
	@echo "Testing mTLS connection to Gateway..."
	@if [ ! -f admin.properties ]; then \
		echo "Certificates not found. Running 'make certs' first..."; \
		$(MAKE) certs; \
	fi
	@echo ""
	@echo "Listing topics as admin..."
	kafka-topics --bootstrap-server localhost:6969 \
		--command-config admin.properties --list

# Help
help:
	@echo "Conduktor Quick Start Makefile"
	@echo ""
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@echo "  all         - Remove, build, run, and setup (default)"
	@echo "  build       - Build Docker image"
	@echo "  run         - Run container"
	@echo "  stop        - Stop container"
	@echo "  rm          - Stop and remove container"
	@echo "  clean       - Remove container, image, and local certs"
	@echo "  logs        - Follow container logs"
	@echo "  setup-logs  - View setup script logs"
	@echo "  setup       - Wait for services and show status"
	@echo "  certs       - Copy certificates from container"
	@echo "  status      - Show connection information"
	@echo "  test        - Test mTLS connection with kafka-topics"
	@echo "  help        - Show this help"
