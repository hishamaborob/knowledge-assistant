IMAGE   := knowledge-assistant:local
COMPOSE := docker compose -f docker/local/docker-compose.yml

# host.docker.internal resolves natively on Mac/Windows Docker Desktop.
# On Linux it requires an explicit host-gateway mapping.
UNAME := $(shell uname -s)
ifeq ($(UNAME),Linux)
  HOST_GATEWAY := --add-host=host.docker.internal:host-gateway
else
  HOST_GATEWAY :=
endif

.PHONY: start stop logs monitoring help
.DEFAULT_GOAL := help

# ─────────────────────────────────────────────────────────────────────────────

start: ## Build image → start infrastructure → run app (full local stack)
	@echo ""
	@echo "▶  Building Docker image..."
	@docker build -f docker/prod/Dockerfile -t $(IMAGE) . --quiet && echo "   Image built: $(IMAGE)"
	@echo ""
	@echo "▶  Starting infrastructure (Postgres + LocalStack)..."
	@$(COMPOSE) up -d --wait
	@echo ""
	@echo "▶  Removing any previous app container..."
	@docker stop ka-app 2>/dev/null || true
	@docker rm   ka-app 2>/dev/null || true
	@echo ""
	@echo "▶  Starting application..."
	@docker run -d --name ka-app $(HOST_GATEWAY) \
	  -p 8080:8080 \
	  -e SPRING_PROFILES_ACTIVE=local \
	  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/knowledge_assistant \
	  -e DB_USERNAME=ka_user \
	  -e DB_PASSWORD=ka_password \
	  -e AWS_ENDPOINT_OVERRIDE=http://host.docker.internal:4566 \
	  -e SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 \
	  $(IMAGE) > /dev/null
	@echo ""
	@echo "▶  Waiting for app to be healthy..."
	@until curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; do \
	  printf "."; sleep 2; done
	@printf "\n\n"
	@echo "✔  Ready"
	@echo "   App        →  http://localhost:8080"
	@echo "   Swagger    →  http://localhost:8080/swagger-ui.html"
	@echo "   Metrics    →  http://localhost:8080/actuator/prometheus"
	@echo "   Health     →  http://localhost:8080/actuator/health"
	@echo ""
	@echo "   Run 'make monitoring' to also start Prometheus + Grafana"
	@echo "   Run 'make logs' to tail app output"
	@echo ""

stop: ## Stop app first, then infrastructure (safe drain order)
	@echo ""
	@echo "▶  Stopping application..."
	@docker stop ka-app 2>/dev/null && echo "   ka-app stopped" || echo "   ka-app was not running"
	@docker rm   ka-app 2>/dev/null || true
	@echo ""
	@echo "▶  Stopping infrastructure..."
	@$(COMPOSE) --profile monitoring down
	@echo ""
	@echo "✔  All services stopped"
	@echo ""

logs: ## Tail live application logs (Ctrl+C to exit)
	docker logs -f ka-app

monitoring: ## Add Prometheus + Grafana to the running stack
	@echo ""
	@$(COMPOSE) --profile monitoring up -d --wait
	@echo ""
	@echo "✔  Monitoring stack ready"
	@echo "   Grafana    →  http://localhost:3000  (admin / admin)"
	@echo "   Prometheus →  http://localhost:9090"
	@echo ""

help: ## Show available commands
	@echo ""
	@echo "Usage: make <command>"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' Makefile \
	  | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'
	@echo ""
