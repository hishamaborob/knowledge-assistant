# Knowledge Assistant — Architecture Documentation

## Table of Contents
1. [System Overview](#system-overview)
2. [System Context Diagram](#system-context-diagram)
3. [Container Diagram](#container-diagram)
4. [Component Diagram](#component-diagram)
5. [Sequence Diagrams](#sequence-diagrams)
6. [RAG Pipeline Design](#rag-pipeline-design)
7. [LLM Provider Abstraction](#llm-provider-abstraction)
8. [Data Model](#data-model)
9. [Security Architecture](#security-architecture)
10. [AWS Architecture](#aws-architecture)

---

## System Overview

Knowledge Assistant is a production-grade Retrieval-Augmented Generation (RAG) system.
Users upload documents; the system ingests, chunks, embeds, and stores them.
At query time, the system retrieves semantically relevant chunks and feeds them to an LLM to produce cited answers.

**Core invariant:** the LLM never answers from its parametric knowledge alone.
Every answer must be grounded in retrieved document chunks with traceable citations.

---

## System Context Diagram

```mermaid
C4Context
    title System Context — Knowledge Assistant

    Person(user, "API Consumer", "Developer or end-user interacting via REST API or future UI")
    Person(admin, "Platform Admin", "Manages infrastructure, monitors system health")

    System(ka, "Knowledge Assistant", "RAG application: ingest documents, answer questions with citations")

    System_Ext(openai, "OpenAI API", "LLM + Embedding provider (gpt-4o, text-embedding-3-large)")
    System_Ext(anthropic, "Anthropic API", "LLM provider (claude-3-5-sonnet)")
    System_Ext(gemini, "Google Gemini API", "LLM provider (gemini-1.5-pro)")
    System_Ext(s3, "AWS S3", "Durable document storage")
    System_Ext(cloudwatch, "AWS CloudWatch", "Log aggregation and alerting")
    System_Ext(secrets, "AWS Secrets Manager", "API keys, DB credentials")

    Rel(user, ka, "Uploads documents, asks questions", "HTTPS/REST")
    Rel(admin, ka, "Monitors, configures", "AWS Console / Grafana")
    Rel(ka, openai, "Generates embeddings + LLM completions", "HTTPS")
    Rel(ka, anthropic, "LLM completions", "HTTPS")
    Rel(ka, gemini, "LLM completions", "HTTPS")
    Rel(ka, s3, "Store and retrieve raw documents", "AWS SDK")
    Rel(ka, cloudwatch, "Emit logs and metrics", "CloudWatch agent")
    Rel(ka, secrets, "Read credentials at startup", "AWS SDK")
```

---

## Container Diagram

```mermaid
C4Container
    title Container Diagram — Knowledge Assistant

    Person(user, "API Consumer")

    Container_Boundary(ka, "Knowledge Assistant Platform") {
        Container(api, "REST API", "Spring Boot 3.x / Java 21", "Handles HTTP requests, auth, validation, rate limiting")
        Container(ingestion, "Ingestion Pipeline", "Spring Batch (optional) / async threads", "Parses, chunks, embeds documents")
        Container(rag, "RAG Orchestrator", "Spring AI", "Retrieves chunks, builds prompts, calls LLM, assembles citations")
        ContainerDb(pg, "PostgreSQL + pgvector", "RDS PostgreSQL 16", "Stores document metadata, chunks, and 1536-dim vector embeddings")
        Container(cache, "Redis Cache", "ElastiCache (optional)", "Caches frequent query embeddings and LLM responses")
    }

    System_Ext(s3, "AWS S3", "Raw document storage")
    System_Ext(llm, "LLM Providers", "OpenAI / Anthropic / Gemini")
    System_Ext(prom, "Prometheus + Grafana", "Metrics and dashboards")

    Rel(user, api, "REST calls", "HTTPS / JWT")
    Rel(api, ingestion, "Triggers async ingestion job", "Internal / ApplicationEvent")
    Rel(api, rag, "Delegates chat queries", "Internal service call")
    Rel(ingestion, s3, "Read uploaded files", "AWS SDK")
    Rel(ingestion, pg, "Persist chunks + embeddings", "Spring Data JPA")
    Rel(rag, pg, "Vector similarity search", "pgvector cosine distance")
    Rel(rag, llm, "Prompt + completion", "HTTP via Spring AI")
    Rel(api, pg, "Document CRUD", "Spring Data JPA")
    Rel(api, prom, "Expose /actuator/prometheus", "HTTP scrape")
```

---

## Component Diagram

```mermaid
C4Component
    title Component Diagram — Backend Application

    Container_Boundary(api_layer, "API Layer (Adapters IN)") {
        Component(doc_ctrl, "DocumentController", "REST Controller", "Document upload, list, delete endpoints")
        Component(chat_ctrl, "ChatController", "REST Controller", "POST /chat endpoint")
        Component(embed_ctrl, "EmbeddingController", "REST Controller", "POST /embeddings/rebuild")
        Component(health_ctrl, "HealthController", "Actuator / REST", "GET /health")
        Component(auth_filter, "JwtAuthFilter", "Servlet Filter", "Validates Bearer tokens")
        Component(rate_limiter, "RateLimitFilter", "Servlet Filter", "Token-bucket rate limiting per API key")
    }

    Container_Boundary(app_layer, "Application Layer (Use Cases)") {
        Component(doc_svc, "DocumentService", "Use Case", "Orchestrates upload → S3 store → ingestion trigger")
        Component(ingest_svc, "IngestionService", "Use Case", "Chunk → embed → persist pipeline")
        Component(chat_svc, "ChatService", "Use Case", "Embed query → retrieve → prompt → LLM → cite")
        Component(embed_svc, "EmbeddingService", "Use Case", "Rebuild embeddings for existing docs")
    }

    Container_Boundary(domain_layer, "Domain Layer (Pure Java)") {
        Component(document, "Document", "Aggregate Root", "Id, metadata, status, S3 key")
        Component(chunk, "DocumentChunk", "Entity", "Text, embedding vector, position, document ref")
        Component(query_result, "QueryResult", "Value Object", "Answer text + list of Citation VOs")
        Component(citation, "Citation", "Value Object", "ChunkId, documentId, score, excerpt")
        Component(chunk_strategy, "ChunkingStrategy", "Domain Interface", "Port for chunking implementations")
        Component(llm_port, "LlmPort", "Domain Interface", "Port for LLM completion")
        Component(embedding_port, "EmbeddingPort", "Domain Interface", "Port for vector generation")
        Component(vector_store_port, "VectorStorePort", "Domain Interface", "Port for similarity search")
        Component(doc_store_port, "DocumentStorePort", "Domain Interface", "Port for file storage")
    }

    Container_Boundary(infra_layer, "Infrastructure Layer (Adapters OUT)") {
        Component(openai_adapter, "OpenAiAdapter", "Spring AI", "Implements LlmPort + EmbeddingPort via OpenAI")
        Component(anthropic_adapter, "AnthropicAdapter", "Spring AI", "Implements LlmPort via Anthropic")
        Component(gemini_adapter, "GeminiAdapter", "Spring AI", "Implements LlmPort via Gemini")
        Component(pgvector_adapter, "PgVectorAdapter", "Spring AI / JPA", "Implements VectorStorePort via pgvector")
        Component(s3_adapter, "S3DocumentAdapter", "AWS SDK v2", "Implements DocumentStorePort via S3")
        Component(jpa_repo, "DocumentJpaRepository", "Spring Data JPA", "CRUD for Document + DocumentChunk")
        Component(text_extractor, "TextExtractorAdapter", "Apache PDFBox / Tika", "Extracts text from PDF, TXT, MD")
        Component(fixed_chunk, "FixedSizeChunker", "Domain Impl", "Implements ChunkingStrategy — fixed token window")
        Component(semantic_chunk, "SemanticChunker", "Domain Impl", "Implements ChunkingStrategy — embedding similarity boundaries")
    }

    Rel(doc_ctrl, doc_svc, "calls")
    Rel(chat_ctrl, chat_svc, "calls")
    Rel(embed_ctrl, embed_svc, "calls")
    Rel(doc_svc, ingest_svc, "triggers async")
    Rel(doc_svc, doc_store_port, "store file")
    Rel(ingest_svc, chunk_strategy, "chunk text")
    Rel(ingest_svc, embedding_port, "generate embeddings")
    Rel(ingest_svc, vector_store_port, "persist chunks+vectors")
    Rel(chat_svc, embedding_port, "embed query")
    Rel(chat_svc, vector_store_port, "similarity search")
    Rel(chat_svc, llm_port, "complete prompt")
    Rel(s3_adapter, doc_store_port, "implements")
    Rel(pgvector_adapter, vector_store_port, "implements")
    Rel(openai_adapter, llm_port, "implements")
    Rel(openai_adapter, embedding_port, "implements")
    Rel(anthropic_adapter, llm_port, "implements")
    Rel(gemini_adapter, llm_port, "implements")
```

---

## Sequence Diagrams

### Document Upload & Ingestion

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant API as DocumentController
    participant DocSvc as DocumentService
    participant S3 as S3DocumentAdapter
    participant EventBus as ApplicationEventPublisher
    participant IngestSvc as IngestionService
    participant Extractor as TextExtractorAdapter
    participant Chunker as ChunkingStrategy
    participant EmbedPort as EmbeddingPort
    participant VecStore as PgVectorAdapter
    participant DB as PostgreSQL

    User->>API: POST /documents (multipart file)
    API->>API: Validate JWT, rate limit, file type
    API->>DocSvc: uploadDocument(file, metadata)
    DocSvc->>DB: INSERT document (status=PENDING)
    DocSvc->>S3: putObject(documentId, bytes)
    S3-->>DocSvc: s3Key
    DocSvc->>DB: UPDATE document (s3Key, status=STORED)
    DocSvc->>EventBus: publish DocumentUploadedEvent(documentId)
    DocSvc-->>API: DocumentResponse(id, status=STORED)
    API-->>User: 202 Accepted {id, status}

    Note over EventBus,IngestSvc: Async processing (virtual thread / @Async)

    EventBus->>IngestSvc: onDocumentUploaded(event)
    IngestSvc->>DB: UPDATE document status=PROCESSING
    IngestSvc->>S3: getObject(s3Key)
    S3-->>IngestSvc: raw bytes
    IngestSvc->>Extractor: extractText(bytes, mimeType)
    Extractor-->>IngestSvc: plainText
    IngestSvc->>Chunker: chunk(text, chunkSize, overlap)
    Chunker-->>IngestSvc: List<TextChunk>
    loop For each chunk batch (size=20)
        IngestSvc->>EmbedPort: embed(List<String>)
        EmbedPort-->>IngestSvc: List<float[]>
        IngestSvc->>VecStore: saveChunks(chunks+vectors)
        VecStore->>DB: INSERT document_chunks (embedding vector)
    end
    IngestSvc->>DB: UPDATE document status=READY, chunkCount=N
```

### Chat / RAG Query

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant API as ChatController
    participant ChatSvc as ChatService
    participant EmbedPort as EmbeddingPort
    participant VecStore as PgVectorAdapter
    participant DB as PostgreSQL
    participant PromptBuilder as PromptBuilder
    participant LLM as LlmPort (active provider)

    User->>API: POST /chat {question, topK, documentIds?}
    API->>API: Validate JWT, rate limit
    API->>ChatSvc: query(ChatRequest)
    ChatSvc->>EmbedPort: embed(question)
    EmbedPort-->>ChatSvc: questionVector (1536-dim)
    ChatSvc->>VecStore: similaritySearch(vector, topK, filters)
    VecStore->>DB: SELECT ... ORDER BY embedding <=> $1 LIMIT $2
    DB-->>VecStore: List<ScoredChunk>
    VecStore-->>ChatSvc: List<ScoredChunk>
    ChatSvc->>ChatSvc: Filter chunks below similarity threshold (e.g. 0.75)
    ChatSvc->>PromptBuilder: buildPrompt(question, chunks)
    PromptBuilder-->>ChatSvc: SystemPrompt + UserPrompt
    ChatSvc->>LLM: complete(prompt)
    LLM-->>ChatSvc: LlmResponse(text, tokens)
    ChatSvc->>ChatSvc: buildCitations(usedChunks)
    ChatSvc-->>API: QueryResult(answer, citations, tokenUsage)
    API-->>User: 200 OK {answer, citations, metadata}
```

### AWS Deployment

```mermaid
sequenceDiagram
    autonumber
    actor Dev
    participant GH as GitHub Actions
    participant ECR as AWS ECR
    participant TF as Terraform
    participant ECS as ECS Fargate
    participant RDS as RDS PostgreSQL
    participant SM as Secrets Manager
    participant CW as CloudWatch

    Dev->>GH: git push / PR merge to main
    GH->>GH: mvn test (unit + integration via Testcontainers)
    GH->>GH: docker build + trivy scan
    GH->>ECR: docker push :latest + :{sha}
    GH->>TF: terraform plan (on PR)
    GH->>TF: terraform apply (on merge to main)
    TF->>RDS: Provision RDS PostgreSQL + pgvector extension
    TF->>ECS: Update task definition (new image digest)
    ECS->>SM: Fetch DB_URL, API keys at container start
    ECS->>RDS: Flyway migration on startup
    ECS->>CW: Stream logs + emit custom metrics
    GH-->>Dev: Deployment complete notification
```

---

## RAG Pipeline Design

### Chunking Strategies

| Strategy | Description | Best For | Tradeoff |
|---|---|---|---|
| **Fixed-size** | Split on token count with overlap | Simple docs, fast ingestion | May split sentences mid-thought |
| **Sentence-boundary** | Split at sentence boundaries within window | General purpose | Slight complexity |
| **Semantic** | Split where embedding similarity drops | Dense technical docs | Expensive — 2× embedding calls |
| **Recursive character** | LangChain-style, tries larger → smaller separators | Code + prose | Good default |

**Implementation plan:** Start with fixed-size (512 tokens, 64 overlap). Add semantic chunking in Phase 7.

### Embedding Model Selection

| Model | Dimensions | Cost | Quality | Notes |
|---|---|---|---|---|
| `text-embedding-3-small` | 1536 | $0.02/1M tokens | Good | Default choice |
| `text-embedding-3-large` | 3072 | $0.13/1M tokens | Best | Use for production |
| `text-embedding-ada-002` | 1536 | $0.10/1M tokens | Good | Legacy, avoid for new |

**Rule:** embedding model must be **immutable** after first ingestion. Changing it requires a full re-embed (the `/embeddings/rebuild` endpoint exists for this).

### Vector Index Strategy

```sql
-- IVFFlat: good for < 1M vectors, fast build
CREATE INDEX ON document_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- HNSW: better recall, higher memory, better for > 1M vectors
CREATE INDEX ON document_chunks USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
```

**Phase 2 default:** HNSW. More memory but better recall and no training step required.

### Hybrid Search (Phase 7+ evolution)

```
score = α × vector_similarity + (1-α) × bm25_score
```

Requires `pg_trgm` or `ts_vector` full-text search alongside pgvector. Significantly improves recall for keyword-heavy queries.

### Reranking (Phase 7+ evolution)

After retrieving top-K=20 via vector search, pass all 20 chunks through a cross-encoder reranker (Cohere Rerank API or local model), then take top-K=5 for the prompt. Dramatically improves precision at the cost of ~200ms latency.

---

## LLM Provider Abstraction

The `LlmPort` domain interface decouples the application from any specific provider:

```
LlmPort
  └── OpenAiAdapter     (Spring AI OpenAI chat client)
  └── AnthropicAdapter  (Spring AI Anthropic chat client)
  └── GeminiAdapter     (Spring AI Google Vertex/Gemini client)
```

Provider selection is driven by `app.llm.provider=openai|anthropic|gemini` in application config.
Spring `@ConditionalOnProperty` activates the correct `@Bean`.

**When to use AWS Bedrock instead:**
- You are already in AWS and want to avoid external egress
- Compliance requires all data to stay within your AWS VPC (use Bedrock VPC endpoints)
- You want unified IAM-based auth instead of managing API keys
- You need access to Titan Embeddings for cost-optimized embedding at scale
- Bedrock supports Claude, Llama, Titan — if your org already uses Claude via Anthropic API, Bedrock lets you avoid a second vendor relationship

---

## Data Model

```mermaid
erDiagram
    DOCUMENTS {
        uuid id PK
        varchar name
        varchar original_filename
        varchar mime_type
        varchar s3_key
        varchar status
        int chunk_count
        bigint file_size_bytes
        varchar created_by
        timestamp created_at
        timestamp updated_at
    }

    DOCUMENT_CHUNKS {
        uuid id PK
        uuid document_id FK
        int chunk_index
        text content
        vector embedding
        int token_count
        varchar page_number
        jsonb metadata
        timestamp created_at
    }

    CHAT_SESSIONS {
        uuid id PK
        varchar user_id
        timestamp created_at
    }

    CHAT_MESSAGES {
        uuid id PK
        uuid session_id FK
        varchar role
        text content
        jsonb citations
        int prompt_tokens
        int completion_tokens
        varchar model_used
        timestamp created_at
    }

    DOCUMENTS ||--o{ DOCUMENT_CHUNKS : "has"
    CHAT_SESSIONS ||--o{ CHAT_MESSAGES : "contains"
```

---

## Security Architecture

```
Request → TLS Termination (ALB)
        → JwtAuthFilter (RS256 JWT validation)
        → RateLimitFilter (per API key, token bucket)
        → Controller
        → Application Layer
        → Infrastructure (credentials from Secrets Manager, never env vars in prod)
```

- **JWT:** RS256, short-lived (15 min access token), supports API key as alternative
- **Secrets:** Never hardcode. Dev: `application-local.yml` (gitignored). Prod: Secrets Manager via Spring Cloud AWS
- **S3:** Pre-signed URLs for download, never expose raw S3 URLs
- **DB:** RDS in private subnet, no public access, Security Group allows only ECS tasks

---

## AWS Architecture

```mermaid
graph TB
    subgraph Internet
        User[API Consumer]
    end

    subgraph AWS["AWS Region (us-east-1)"]
        subgraph Public["Public Subnets"]
            ALB[Application Load Balancer]
        end

        subgraph Private["Private Subnets"]
            subgraph ECS["ECS Fargate Cluster"]
                Task[knowledge-assistant Task\n2 vCPU / 4GB RAM]
            end

            subgraph Data["Data Tier"]
                RDS[(RDS PostgreSQL 16\npgvector extension)]
            end
        end

        subgraph Services["AWS Services"]
            S3[(S3 Bucket\nDocument Storage)]
            SM[Secrets Manager\nAPI Keys + DB Creds]
            ECR[ECR\nContainer Registry]
            CW[CloudWatch\nLogs + Metrics]
        end
    end

    subgraph External["External LLM APIs"]
        OAI[OpenAI API]
        ANT[Anthropic API]
        GEM[Google Gemini API]
    end

    User --> ALB
    ALB --> Task
    Task --> RDS
    Task --> S3
    Task --> SM
    Task --> CW
    Task --> OAI
    Task --> ANT
    Task --> GEM
    ECR --> Task
```
