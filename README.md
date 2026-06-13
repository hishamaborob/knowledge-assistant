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

## Quick Start (Local)

```bash
# Start PostgreSQL with pgvector
docker compose -f docker/local/docker-compose.yml up -d

# Run the application
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

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
| Containers | Docker, Docker Compose |
| Observability | Micrometer, Prometheus, Grafana |
| Testing | JUnit 5, Testcontainers, Mockito |
| CI/CD | GitHub Actions |

---

## Development Phases

| Phase | Description | Status |
|---|---|---|
| 1 | Project bootstrap — Spring Boot, Maven, Hexagonal skeleton | Planned |
| 2 | PostgreSQL + pgvector — Flyway migrations, JPA entities | Planned |
| 3 | Document ingestion — S3, text extraction, parsing | Planned |
| 4 | Embedding generation — OpenAI embeddings, chunking strategies | Planned |
| 5 | Vector search — pgvector similarity search, filtering | Planned |
| 6 | LLM integration — provider abstraction, prompt engineering | Planned |
| 7 | RAG orchestration — full pipeline, citations, hybrid search | Planned |
| 8 | AWS deployment — Terraform, ECS Fargate, RDS | Planned |
| 9 | Observability — Micrometer, Prometheus, Grafana dashboards | Planned |
| 10 | Production hardening — security, rate limiting, resilience | Planned |

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
│   └── terraform/
│       ├── modules/            # Reusable Terraform modules
│       │   ├── security/       # VPC, Security Groups
│       │   ├── rds/            # RDS PostgreSQL
│       │   ├── ecs/            # ECS Fargate cluster + service
│       │   ├── s3/             # S3 buckets
│       │   ├── iam/            # IAM roles and policies
│       │   └── monitoring/     # CloudWatch, alarms
│       └── environments/
│           ├── dev/
│           └── prod/
├── docker/
│   ├── local/                  # docker-compose for local dev
│   └── prod/                   # Production Dockerfile
├── docs/
│   ├── architecture/           # C4 diagrams, ADRs
│   ├── adr/                    # Architecture Decision Records
│   └── api/                    # OpenAPI spec
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
| POST | `/chat` | Ask a question, get a cited answer |
| POST | `/embeddings/rebuild` | Rebuild embeddings for a document |
| GET | `/actuator/health` | Health check |
| GET | `/actuator/prometheus` | Prometheus metrics |

---

## License

MIT
