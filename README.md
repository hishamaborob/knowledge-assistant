# Knowledge Assistant

A production-grade **Retrieval-Augmented Generation (RAG)** system built with Java 21, Spring Boot 3.x, Spring AI, and PostgreSQL pgvector.

Upload documents → Ask questions → Get cited answers grounded in your content.

---

## Architecture

See [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) for full C4 diagrams, sequence diagrams, and design decisions.

**Key design principles:**
- Hexagonal Architecture (Ports & Adapters) — domain has zero framework dependencies
- Provider-agnostic LLM layer — swap OpenAI / Anthropic / Gemini via config
- Async ingestion pipeline — uploads return immediately; processing is event-driven
- Every answer includes traceable citations — no hallucination-only responses

---

## Quick Start

### With Make (recommended)

Prerequisites: Docker, Ollama running locally with `nomic-embed-text` + `llama3.2` pulled.

```bash
make start       # Build image → start Postgres + LocalStack → run app → wait for /health
make stop        # Stop app first, then all infrastructure (safe drain order)
make logs        # Tail live app output
make monitoring  # Add Prometheus + Grafana to a running stack (localhost:3000)
```

Run `make` with no arguments to list all commands.

### Manual (Spring Boot dev mode)

Useful for faster iteration — no Docker image rebuild on code changes.

```bash
# 1. Start infrastructure
docker compose -f docker/local/docker-compose.yml up -d

# 2. Pull Ollama models (first time only)
ollama pull nomic-embed-text
ollama pull llama3.2

# 3. Run the app
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Once started, the app is available at:

| Endpoint | URL |
|---|---|
| App | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| Metrics | http://localhost:8080/actuator/prometheus |
| Grafana | http://localhost:3000 (after `make monitoring`) |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (virtual threads) |
| Framework | Spring Boot 3.x |
| AI Framework | Spring AI |
| Database | PostgreSQL 16 + pgvector |
| ORM | Spring Data JPA / Hibernate |
| Build | Maven |
| Cloud | AWS (ECS Fargate, RDS, S3, Secrets Manager) |
| IaC | Terraform |
| Secrets | Spring Cloud AWS Secrets Manager |
| Containers | Docker, Docker Compose |
| Observability | Micrometer, Prometheus, Grafana |
| Testing | JUnit 5, Testcontainers, Mockito |
| CI/CD | GitHub Actions |
| Dev tooling | Make (local lifecycle) |

---

## Development Phases

| Phase | Description | Status |
|---|---|---|
| 1 | Project bootstrap — Spring Boot, Maven, hexagonal skeleton, ArchUnit | ✅ Complete |
| 2 | PostgreSQL + pgvector — Flyway migrations, JPA entities, HNSW index | ✅ Complete |
| 3 | Document ingestion — S3 upload, text extraction (PDF/TXT/MD), async pipeline | ✅ Complete |
| 4 | Chunking + embeddings — Ollama nomic-embed-text (768 dims), OpenAI fallback | ✅ Complete |
| 5 | Query API — embed question → pgvector search → LLM answer + source citations | ✅ Complete |
| 6 | Multi-provider LLM — Anthropic adapter + improved prompt engineering | ✅ Complete |
| 7 | Conversation history — chat sessions, multi-turn context | ✅ Complete |
| 8 | AWS deployment — Terraform, ECS Fargate, RDS, Secrets Manager, CD pipeline | ✅ Complete |
| 9 | Observability — Micrometer metrics, Grafana dashboards, LLM-as-a-judge evaluation | ✅ Complete |
| 10 | Production hardening — JWT auth, rate limiting, circuit breakers, resilience | Planned |

---

## Project Structure

```
knowledge-assistant/
├── backend/                    # Spring Boot application
│   ├── src/main/java/com/kbassistant/
│   │   ├── api/                # REST controllers, DTOs, filters (adapters IN)
│   │   ├── application/        # Use cases, application services
│   │   ├── domain/             # Entities, VOs, ports (interfaces), domain services
│   │   └── infrastructure/     # Adapters OUT: JPA, S3, LLM providers, extractors
│   ├── src/main/resources/
│   │   ├── db/migration/       # Flyway SQL migrations
│   │   └── application.yml
│   └── pom.xml
├── infrastructure/
│   ├── README.md               # Deploy guide, cost breakdown, teardown
│   └── terraform/
│       ├── modules/            # Reusable Terraform modules
│       │   ├── vpc/            # VPC, subnets, NAT Gateway
│       │   ├── security/       # Security groups (ALB, ECS, RDS)
│       │   ├── ecr/            # Container registry
│       │   ├── rds/            # RDS PostgreSQL 16 (pgvector)
│       │   ├── s3/             # Document storage bucket
│       │   ├── secrets/        # Secrets Manager secret
│       │   ├── iam/            # Task execution role + task role
│       │   ├── alb/            # Application Load Balancer, optional HTTPS
│       │   └── ecs/            # ECS cluster, service, autoscaling
│       ├── environments/
│       │   └── prod/           # Wires all modules; S3 backend state
│       └── bootstrap/          # One-time: S3 + DynamoDB for Terraform state
├── docker/
│   ├── local/                  # docker-compose for local dev
│   └── prod/                   # Production Dockerfile
├── docs/
│   ├── architecture/           # C4 diagrams, ADRs
│   ├── adr/                    # Architecture Decision Records
│   └── api/                    # OpenAPI spec
├── Makefile                    # Local dev lifecycle (make start / stop / logs / monitoring)
├── scripts/                    # Dev utility scripts
└── .github/
    └── workflows/              # CI/CD pipelines
```

---

## API Reference

| Method | Path | Description |
|---|---|---|
| POST | `/documents` | Upload a document (PDF, TXT, MD) |
| GET | `/documents` | List all documents |
| GET | `/documents/{id}` | Get document details and status |
| DELETE | `/documents/{id}` | Delete document and its chunks |
| POST | `/queries` | Ask a question, get a cited answer (stateless) |
| POST | `/sessions` | Create a chat session |
| POST | `/sessions/{id}/messages` | Send a message in a session (multi-turn, history-aware) |
| GET | `/sessions/{id}/messages` | Retrieve full conversation history for a session |
| GET | `/actuator/health` | Health check |
| GET | `/actuator/prometheus` | Prometheus metrics |

---

## License

MIT
