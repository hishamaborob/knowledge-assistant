# Phase 1 — Project Bootstrap: Detailed Implementation Plan

## What We're Building

A running Spring Boot 3.x application with:
- Correct hexagonal package structure (enforced by ArchUnit)
- All Maven dependencies declared with proper BOM management
- Docker Compose for local PostgreSQL + pgvector
- Flyway baseline migration
- Health endpoint
- The first domain entity (`Document`) and first port (`DocumentStorePort`)
- Smoke tests

## Why This Phase Matters

The hexagonal skeleton is the most important thing to get right. Once the package structure and
dependency rules are established, every subsequent phase adds implementations without touching
the architecture. Getting this wrong means:
- Framework imports leak into domain (kills testability)
- Provider swap requires touching business logic
- Tests become slow (need Spring context for everything)

## Task List

### T1-01: `pom.xml`
Full Maven POM with:
- Spring Boot 3.4.x parent
- Java 21 source/target
- Spring AI BOM (latest stable)
- All required dependencies (see below)
- Maven compiler plugin with `-parameters` flag (required for Spring)
- Maven Surefire plugin configured for JUnit 5
- Failsafe plugin for integration tests (`*IT.java`)

### T1-02: Package Skeleton
```
com.kbassistant/
├── KnowledgeAssistantApplication.java
├── api/
│   ├── controller/
│   ├── dto/
│   └── filter/
├── application/
│   ├── service/
│   └── event/
├── domain/
│   ├── model/          ← aggregate roots, entities, value objects
│   ├── port/
│   │   ├── in/         ← use case interfaces (optional for this scale)
│   │   └── out/        ← repository and external service ports
│   └── exception/
└── infrastructure/
    ├── persistence/    ← JPA entities, repositories, adapters
    ├── storage/        ← S3 adapter
    ├── llm/            ← LLM provider adapters
    ├── embedding/      ← Embedding provider adapters
    ├── extraction/     ← Text extraction adapters
    └── config/         ← Spring @Configuration classes
```

### T1-03: Docker Compose (Local)
```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: knowledge_assistant
      POSTGRES_USER: ka_user
      POSTGRES_PASSWORD: ka_password
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql/data"]
```

### T1-04: application.yml + application-local.yml
- `application.yml`: shared config, actuator endpoints, logging
- `application-local.yml`: local DB URL, dummy API keys, LocalStack endpoints

### T1-05: Flyway V1 Migration
```sql
-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;
-- Baseline schema version tracking
```

### T1-06: Domain Model — `Document`
Pure Java class (no JPA, no Spring):
```java
public class Document {
    private DocumentId id;
    private String name;
    private String originalFilename;
    private MimeType mimeType;
    private DocumentStatus status;
    private String s3Key;
    private int chunkCount;
    private Instant createdAt;
    // domain methods: markStored(), startProcessing(), markReady(), markFailed()
}
```

### T1-07: Domain Ports
```java
// out port
public interface DocumentStorePort {
    String store(DocumentId id, byte[] content, String filename, MimeType mimeType);
    byte[] retrieve(String s3Key);
    void delete(String s3Key);
}

// out port  
public interface DocumentRepository {
    Document save(Document document);
    Optional<Document> findById(DocumentId id);
    List<Document> findAll();
    void delete(DocumentId id);
}
```

### T1-08: ArchUnit Test
```java
@AnalyzeClasses(packages = "com.kbassistant")
class ArchitectureTest {
    @ArchTest
    ArchRule domainHasNoFrameworkDependencies = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "jakarta.persistence..");
    
    @ArchTest
    ArchRule apiDoesNotDependOnInfrastructure = noClasses()
        .that().resideInAPackage("..api..")
        .should().dependOnClassesThat()
        .resideInAPackage("..infrastructure..");
}
```

### T1-09: Smoke Test
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class ApplicationSmokeIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");
    
    @Test
    void healthEndpointReturnsUp() { ... }
    
    @Test  
    void flywayMigrationsRunSuccessfully() { ... }
}
```

## Acceptance Criteria

- [ ] `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` starts in < 10 seconds
- [ ] `GET /actuator/health` returns `{"status":"UP"}`
- [ ] `GET /actuator/info` returns build info
- [ ] Flyway shows all migrations applied in logs
- [ ] `./mvnw verify` runs unit + integration tests, all pass
- [ ] ArchUnit test catches a deliberate violation if you add a Spring import to domain
- [ ] No compiler warnings

## Dependencies to Include in pom.xml

```xml
<!-- Spring Boot Starters -->
spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-actuator
spring-boot-starter-validation
spring-boot-starter-aop  ← for resilience4j

<!-- Spring AI (via BOM) -->
spring-ai-bom (import)
spring-ai-openai-spring-boot-starter
spring-ai-anthropic-spring-boot-starter
spring-ai-vertex-ai-gemini-spring-boot-starter
spring-ai-pgvector-store-spring-boot-starter

<!-- Database -->
org.postgresql:postgresql
org.flywaydb:flyway-core
org.flywaydb:flyway-database-postgresql

<!-- AWS -->
software.amazon.awssdk:bom (import)
software.amazon.awssdk:s3
software.amazon.awssdk:secretsmanager

<!-- Document Parsing -->
org.apache.pdfbox:pdfbox:3.x
org.apache.tika:tika-core
org.apache.tika:tika-parsers-standard-package

<!-- Resilience -->
io.github.resilience4j:resilience4j-spring-boot3
io.github.resilience4j:resilience4j-retry

<!-- Observability -->
io.micrometer:micrometer-registry-prometheus
io.micrometer:micrometer-tracing-bridge-otel  ← OpenTelemetry tracing

<!-- API Docs -->
org.springdoc:springdoc-openapi-starter-webmvc-ui

<!-- Testing -->
testcontainers bom (import)
testcontainers:junit-jupiter
testcontainers:postgresql
spring-boot-starter-test
com.tngtech.archunit:archunit-junit5

<!-- Utilities -->
org.projectlombok:lombok
com.fasterxml.jackson.core:jackson-databind  ← included by starter-web
```

## Git Commit Message (after Phase 1 complete)

```
feat: bootstrap knowledge-assistant with hexagonal architecture skeleton

- Java 21 Spring Boot 3.x Maven project with full dependency manifest
- Hexagonal package structure: domain / application / api / infrastructure
- Docker Compose with pgvector/pgvector:pg16 for local development
- Flyway baseline migration enabling pgvector extension
- Domain model: Document aggregate root + DocumentStorePort + DocumentRepository ports
- ArchUnit test enforcing zero framework imports in domain layer
- Smoke test via Testcontainers validating startup and health endpoint
```

## PR Description Template

```markdown
## Phase 1: Project Bootstrap

### What
Establishes the foundational hexagonal architecture skeleton for Knowledge Assistant.

### Key Decisions
- **Package structure** follows strict hexagonal boundaries enforced by ArchUnit in CI.
  Violations are build-breakers, not warnings.
- **Domain layer** is pure Java — zero Spring or JPA imports. This means domain logic
  tests run without a Spring context.
- **pgvector/pgvector:pg16** Docker image bundles the extension — no manual 
  `CREATE EXTENSION` required in CI containers.
- **Spring AI BOM** pins all AI dependency versions to a tested set — avoids 
  transitive version conflicts between Spring AI modules.

### What's Not Here Yet
- No actual ingestion logic (Phase 3)
- No embeddings (Phase 4)
- Infrastructure adapters are stubs (Phase 2 adds real JPA + pgvector)

### Test Plan
- [ ] `./mvnw verify` passes locally
- [ ] Docker Compose starts cleanly
- [ ] `/actuator/health` returns UP
- [ ] ArchUnit test blocks a domain class with Spring imports
```

---

## Ready to Begin?

Once you approve this plan, I will generate code in this order:
1. `pom.xml` — full dependency manifest with explanations
2. Package skeleton — all placeholder classes
3. `application.yml` + `application-local.yml`
4. `docker-compose.yml`
5. Flyway V1 migration
6. Domain model: `Document`, `DocumentId`, `DocumentStatus`, `MimeType`
7. Domain ports: `DocumentStorePort`, `DocumentRepository`
8. `KnowledgeAssistantApplication.java`
9. ArchUnit test
10. Smoke test (Testcontainers)

**Reply "approve phase 1" to begin code generation.**
