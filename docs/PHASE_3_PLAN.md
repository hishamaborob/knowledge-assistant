# Phase 3 — Document Ingestion Pipeline

## Goal

End-to-end upload flow: `POST /documents` → S3 → text extraction → async event.
No embeddings yet — that is Phase 4's job.

## What Phase 3 Delivers

- `POST /documents` accepts a multipart file, stores it to S3, returns 202 with `documentId`
- `GET /documents/{id}` and `GET /documents` list documents with current status
- Async ingestion pipeline driven by Spring `ApplicationEvent`
- Text extraction for PDF (PDFBox), TXT, Markdown
- Status machine exercised end-to-end: `PENDING → STORED → PROCESSING`
- `FAILED` status captured with error message on any pipeline exception
- `TextExtractedEvent` published (consumed in Phase 4 by the embedding step)

**Not in scope:** embedding generation, chunking, vector storage (Phase 4).

---

## Architecture Decisions

### Why the document stays in PROCESSING after Phase 3

The ingestion pipeline spans two phases:
- Phase 3: upload → S3 → extract text → publish `TextExtractedEvent`
- Phase 4: handle `TextExtractedEvent` → chunk → embed → store vectors → READY

Marking the document READY in Phase 3 with zero chunks would mean Phase 4 needs to reverse
a terminal state, which contradicts the state machine (`READY` is a terminal status).
Instead, the document stays in `PROCESSING` after successful text extraction.
The integration test therefore asserts `PROCESSING`, not `READY`.

### Why `POST /documents` returns 202, not 201

The S3 upload is synchronous (the upload itself completes before we return).
But text extraction and (in Phase 4) embedding are async. The client gets a `documentId`
immediately and polls `GET /documents/{id}` for status. 202 Accepted is the correct
HTTP semantics for "we received your request; processing is underway."

### Why `UploadDocumentCommand` lives in the application layer, not the API layer

The in-port (`UploadDocumentUseCase`) must not depend on Spring Web types like
`MultipartFile`. The API controller converts the multipart request to an
`UploadDocumentCommand` record — a plain Java value object — before calling the use case.
This means `DocumentService` can be tested without any HTTP context, using just the command.

### Why `ApplicationEvent` over a message broker for Phase 3

A message broker (Kafka, RabbitMQ) adds operational complexity — you need another service
running locally and in CI, and the tests need another Testcontainers image. Spring's
`ApplicationEvent` mechanism is in-process, requires nothing external, and is sufficient
for the failure domain of a single-JVM ingestion pipeline. If we ever scale to multiple
JVM instances, Phase 10 is where we'd introduce a broker.

The tradeoff: in-process events mean if the JVM crashes during ingestion, the event is lost.
For the portfolio scope this is acceptable; production hardening would add a retry queue.

### Why `TextExtractorPort` is a domain port

Text extraction is a domain concern — we need the text to build the knowledge base — but the
implementation technology (PDFBox) is an infrastructure detail. Port in domain, adapter in
infrastructure. Swapping from PDFBox to Apache Tika (for broader format support) would mean
writing a new adapter without touching the domain.

### S3 key format: `documents/{documentId}/{filename}`

Groups all files for a document under a single prefix. Useful for:
- Easy deletion of a document's storage (prefix-based S3 delete)
- Debuggability — find a file in S3 by document ID without a DB lookup
- Consistent retrieval in `DocumentStorePort.retrieve(storageKey)`

### LocalStack for local S3 (already in docker-compose)

`S3Client` is configured with an optional endpoint override via `app.s3.endpoint`.
In production (ECS Fargate with IAM task role), the endpoint is empty and the SDK uses the
default AWS endpoint with the task role credentials. Locally, `application-local.yml` sets
`app.s3.endpoint: http://localhost:4566`.

---

## Files to Create

### Domain — new in-port

| File | Notes |
|---|---|
| `domain/port/in/UploadDocumentUseCase.java` | In-port interface; `DocumentController` depends on this, not the concrete service |

### Application — new

| File | Notes |
|---|---|
| `application/command/UploadDocumentCommand.java` | Record: name, originalFilename, content bytes, mimeType, fileSizeBytes, uploadedBy |
| `application/event/DocumentUploadedEvent.java` | Extends `ApplicationEvent`; carries DocumentId + s3Key |
| `application/event/TextExtractedEvent.java` | Extends `ApplicationEvent`; carries DocumentId + extractedText (String) |
| `application/service/DocumentService.java` | Implements `UploadDocumentUseCase`; validates MIME → store S3 → save → publish event |
| `application/service/IngestionService.java` | `@EventListener` + `@Async`; marks PROCESSING → extract text → publish `TextExtractedEvent`; marks FAILED on exception |

### Domain — new out-port

| File | Notes |
|---|---|
| `domain/port/out/TextExtractorPort.java` | `String extract(byte[] content, MimeType mimeType)` |

### API layer — new

| File | Notes |
|---|---|
| `api/controller/DocumentController.java` | `POST /documents`, `GET /documents/{id}`, `GET /documents` |
| `api/dto/DocumentResponse.java` | Record: id, name, status, mimeType, fileSizeBytes, chunkCount, createdAt, updatedAt, errorMessage |
| `api/exception/GlobalExceptionHandler.java` | `@RestControllerAdvice`; maps domain exceptions to HTTP status codes |

### Infrastructure — new

| File | Notes |
|---|---|
| `infrastructure/storage/S3Properties.java` | `@ConfigurationProperties("app.s3")`; bucketName, region, endpoint |
| `infrastructure/storage/S3Config.java` | `@Bean S3Client` with optional endpoint override for LocalStack |
| `infrastructure/storage/S3DocumentAdapter.java` | Implements `DocumentStorePort` via AWS SDK v2 S3Client |
| `infrastructure/extraction/TextExtractorAdapter.java` | Implements `TextExtractorPort`; PDFBox for PDF, UTF-8 passthrough for TXT/MD |

### Config changes

| File | Change |
|---|---|
| `application.yml` | Add `app.s3.*` and `spring.servlet.multipart.max-file-size: 50MB` |
| `application-local.yml` | Add `app.s3.endpoint: http://localhost:4566` |

### Tests — new

| File | Type | What it proves |
|---|---|---|
| `domain/model/DocumentStatusTransitionTest.java` | Unit | State machine: valid + invalid transitions |
| `application/service/DocumentServiceTest.java` | Unit (Mockito) | Upload validates MIME, stores to S3, publishes event, returns domain object |
| `infrastructure/extraction/TextExtractorAdapterTest.java` | Unit | PDF/TXT/MD extraction returns non-empty text |
| `infrastructure/storage/S3DocumentAdapterTest.java` | Unit (mocked S3) | store/retrieve/delete use correct key format and S3 API calls |
| `api/controller/DocumentControllerIT.java` | Spring MVC (`@WebMvcTest`) | HTTP 202, 200, 404, 415 response codes and response body shape |

---

## Sequence: Upload Flow

```
Client
  │
  ▼
DocumentController.upload(MultipartFile)
  │  convert to UploadDocumentCommand
  ▼
DocumentService.upload(command)          ← @Transactional
  ├─ validate MimeType
  ├─ DocumentStorePort.store()           → S3DocumentAdapter → S3
  ├─ Document.create() + markStored()
  ├─ DocumentRepository.save()           → DB: status = STORED
  ├─ applicationEventPublisher.publishEvent(DocumentUploadedEvent)
  └─ return Document                     → Controller maps to DocumentResponse (202)

  [async — on virtual thread executor]
IngestionService.handleUploadedEvent(DocumentUploadedEvent)
  ├─ DocumentRepository.findById()
  ├─ document.startProcessing()
  ├─ DocumentRepository.save()           → DB: status = PROCESSING
  ├─ DocumentStorePort.retrieve(s3Key)   → S3DocumentAdapter → S3 → bytes
  ├─ TextExtractorPort.extract()         → TextExtractorAdapter → PDFBox / UTF-8
  ├─ applicationEventPublisher.publishEvent(TextExtractedEvent)
  └─ [catch] document.markFailed(msg)    → DB: status = FAILED
```

---

## Acceptance Criteria

- [ ] `POST /documents` with a PDF returns 202 + JSON `{"documentId":"...", "status":"STORED"}`
- [ ] `GET /documents/{id}` returns 200 with current status
- [ ] After async processing completes, `GET /documents/{id}` returns status `PROCESSING`
- [ ] `POST /documents` with `.exe` or `.html` returns 415
- [ ] Corrupt PDF marks document `FAILED` with an error message
- [ ] `mvn verify` green (all unit + IT tests pass)
- [ ] ArchUnit: `DocumentService` and `IngestionService` import nothing from `api` or `infrastructure`

---

## Exception → HTTP Status Mapping

| Exception | HTTP |
|---|---|
| `DocumentNotFoundException` | 404 Not Found |
| `UnsupportedMimeTypeException` (new) | 415 Unsupported Media Type |
| `MaxUploadSizeExceededException` | 413 Payload Too Large |
| `IllegalArgumentException` | 400 Bad Request |
| uncaught `Exception` | 500 Internal Server Error |

---

## Git Commit Message

```
feat: Phase 3 — document upload, S3 storage, text extraction pipeline

Upload flow:
- POST /documents (multipart, 202) → S3 store → PENDING→STORED
- GET /documents/{id}, GET /documents list endpoints
- GlobalExceptionHandler: DocumentNotFoundException→404, UnsupportedMimeType→415

Async ingestion pipeline (Spring ApplicationEvent + virtual thread executor):
- DocumentUploadedEvent → IngestionService: STORED→PROCESSING→extract text
- TextExtractedEvent published for Phase 4 embedding step
- Exception in any step: FAILED + error message persisted

Infrastructure:
- S3DocumentAdapter: AWS SDK v2 S3Client, key = documents/{documentId}/{filename}
- S3Config: endpoint override for LocalStack (app.s3.endpoint property)
- TextExtractorAdapter: PDFBox for PDF, UTF-8 passthrough for TXT/MD

Tests:
- DocumentServiceTest (Mockito): validates MIME, S3 call, event published
- TextExtractorAdapterTest: PDF/TXT/MD extraction
- S3DocumentAdapterTest: correct key format and SDK interactions
- DocumentControllerIT (@WebMvcTest): HTTP contract

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

---

## PR Description

```markdown
## Phase 3: Document Upload + Ingestion Pipeline

### What
End-to-end upload flow from HTTP multipart to S3 to text extraction.

### Pipeline design
`POST /documents` is synchronous to S3 storage and returns 202 immediately.
Async ingestion runs on virtual threads via Spring `ApplicationEvent`:

1. `DocumentUploadedEvent` → `IngestionService` extracts text
2. `TextExtractedEvent` published — consumed by Phase 4 embedding step

The document stays in `PROCESSING` after Phase 3. `READY` is only reachable
once Phase 4 stores the embedding vectors (terminal state requires chunk data).

### Key decisions
- **`ApplicationEvent` over Kafka**: in-process events are sufficient for a
  single-JVM ingestion pipeline; adds nothing to the operational footprint.
  A broker would be warranted if we scale to multiple instances (Phase 10 consideration).
- **`UploadDocumentCommand` in application layer**: keeps Spring Web types out of the
  use case port; `DocumentService` is testable without HTTP context.
- **`TextExtractorPort` in domain**: extraction is a domain concern, PDFBox is an
  infrastructure detail — standard ports/adapters split.
- **LocalStack S3**: `S3Client` uses an optional endpoint override
  (`app.s3.endpoint`) so local dev doesn't need a real AWS account.

### Not included
- Embedding generation (Phase 4)
- JWT auth (Phase 10)
```
