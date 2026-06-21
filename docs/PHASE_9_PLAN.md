# Phase 9 — Observability + LLM-as-a-Judge Evaluation

## Goal

Two complementary observability concerns addressed in one phase:
- **Operational observability**: custom Micrometer metrics on the four key operations, Prometheus scraping (already wired), auto-provisioned Grafana dashboard, structured JSON logging for prod, CloudWatch alarms in Terraform.
- **Answer quality observability**: async LLM-as-a-judge evaluation on a sampled subset of queries — faithfulness and answer relevance scores flow into the same Prometheus/Grafana pipeline as operational metrics, giving a live quality signal alongside latency and error rates.

---

## What Phase 9 Delivers

### Custom Micrometer Metrics

| Metric | Type | Tags | Where recorded | What it measures |
|---|---|---|---|---|
| `chat.query.duration` | Timer | `provider` | `QueryService.query()` | End-to-end RAG pipeline (embed + search + LLM + source build) |
| `llm.completion.duration` | Timer | `provider` | `QueryService.query()` — LLM call only | LLM latency in isolation; reveals its share of tail latency |
| `similarity.search.results` | DistributionSummary | `outcome=found\|not_found` | `QueryService.query()` — after vector search | Count of chunks returned; outcome tag tracks hit/miss rate |
| `embedding.generation.duration` | Timer | `batch_size` | `EmbeddingService.handleTextExtracted()` | Embedding batch latency; `batch_size` tag shows non-linear scaling |
| `ingestion.pipeline.duration` | Timer | `status=success\|failure` | `EmbeddingService.handleTextExtracted()` | Full per-document pipeline; failure tag splits the distribution |
| `document.ingested` | Counter | `status=success\|failure` | `IngestionService.handleDocumentUploaded()` | Running total of documents through the ingestion pipeline |
| `rag.evaluation.faithfulness` | DistributionSummary | `provider` | `EvaluationService.evaluate()` | LLM judge score: are answer claims supported by retrieved context? |
| `rag.evaluation.answer_relevance` | DistributionSummary | `provider` | `EvaluationService.evaluate()` | LLM judge score: does the answer address the question? |

### Gauge vs Counter vs Timer — why each type was chosen

**Timer** — `chat.query.duration`, `llm.completion.duration`, `embedding.generation.duration`, `ingestion.pipeline.duration`: Any "how long did X take?" question needs a Timer. Micrometer Timers record duration AND count in a single instrument, exposing `_count`, `_sum`, and `_bucket` (histogram) series in Prometheus. This gives rate (via `_count`) and p50/p95/p99 latency (via histogram quantiles) from one meter.

**DistributionSummary** — `similarity.search.results`, `rag.evaluation.faithfulness`, `rag.evaluation.answer_relevance`: Like Timer but for arbitrary numerical values — not durations. A chunk count or a 0–1 quality score is not a time; DistributionSummary records the distribution of these values (p50, p90, max). Exposed as `_count`, `_sum`, `_max` in Prometheus; histogram buckets are available if configured.

**Counter** — `document.ingested`: A monotonically increasing event count where latency is irrelevant. `rate(document_ingested_total[5m])` gives ingestion throughput. Using a Timer here would conflate "how many documents" with "how long did ingestion take" — those are tracked separately by `ingestion.pipeline.duration`.

**Gauge** — not used in Phase 9. Gauges measure current instantaneous values (connection pool size, queue depth). All Phase 9 metrics are event-driven rates or distributions, not point-in-time readings.

**Tag cardinality rule**: Tags must be low-cardinality. `provider` has 3–4 values; `status` and `outcome` have 2 values each; `batch_size` is bounded by `app.embedding.batch-size` (default 20). Never tag with document IDs, session IDs, question text, or any unbounded string — one time-series per unique tag value would exhaust Prometheus storage.

### Where `MeterRegistry` is injected

Application services (`QueryService`, `EmbeddingService`, `IngestionService`, `EvaluationService`) — not domain, not infrastructure. The application layer has complete business context: which provider is active, batch sizes, success/failure status. It is also the correct abstraction level: domain must stay pure Java (ArchUnit-enforced), infrastructure adapters are too low to have the tagging context.

`MeterRegistry` is from `io.micrometer.core.instrument` — not Spring-specific. It doesn't appear in ArchUnit's blocked package list (`org.springframework..`, `jakarta.persistence..`). ArchUnit passes cleanly.

### LLM-as-a-Judge Evaluation (`EvaluationService`)

**Why faithfulness + answer relevance, not full RAGAS:**
RAGAS decomposes faithfulness into atomic statement verification (3–5 LLM calls per query). The single-prompt 0–1 score used here gives roughly 80% of the signal at 20% of the cost. Full RAGAS decomposition is a Phase 10+ upgrade.

**Why not retrieval metrics (hit rate, MRR):**
Hit rate and MRR require ground truth labels (question → expected chunk). No labelled eval dataset exists yet. Faithfulness and relevance are self-contained: they only need the question, retrieved context, and answer — no ground truth.

**Async + sampling (`@Async("evaluationTaskExecutor")`, `app.evaluation.sample-rate: 0.2`):**
Evaluation doubles API cost for evaluated queries and has ~1–3s LLM latency. Async fire-and-forget means the user's response is never delayed by evaluation. A 20% sample rate bounds cost while giving a statistically meaningful signal over time. The `evaluationTaskExecutor` uses virtual threads — consistent with the `ingestionTaskExecutor` pattern.

**Prompt design:** Single-turn, explicit 0.0–1.0 scale, "Return ONLY a decimal number" in both the system prompt and user prompt. The score is clamped to [0, 1] after parsing; non-numeric responses fall back to 0.5 (neutral) with a warning log. Context is truncated to 4,000 chars in the faithfulness prompt to avoid token limit issues on smaller models.

**The evaluation call site:**
```
QueryService.query()
  ├─ embed question
  ├─ similarity search  →  similarity.search.results recorded
  ├─ build context (up to 12,000 chars)
  ├─ LLM call  →  llm.completion.duration recorded
  ├─ evaluationService.evaluate(question, context, answer)  →  fires async, returns immediately
  └─ querySample.stop(queryTimer)  →  chat.query.duration recorded
```

### Grafana Dashboard (locally testable)

Auto-provisioned when `docker compose --profile monitoring up -d`. Grafana reads from:
- `docker/local/grafana/provisioning/datasources/prometheus.yml` — registers Prometheus datasource
- `docker/local/grafana/provisioning/dashboards/dashboard.yml` — scans `dashboards/` directory
- `docker/local/grafana/dashboards/knowledge-assistant.json` — 8-panel dashboard

**8 panels:**

| Panel | Query |
|---|---|
| Query rate (req/min) | `rate(chat_query_duration_seconds_count[1m]) * 60` |
| Query P95 latency | `histogram_quantile(0.95, rate(chat_query_duration_seconds_bucket[5m]))` |
| LLM P95 latency | `histogram_quantile(0.95, rate(llm_completion_duration_seconds_bucket[5m]))` |
| Search hit rate by outcome | `rate(similarity_search_results_count[5m])` by `outcome` |
| Ingestion pipeline by status | `rate(ingestion_pipeline_duration_seconds_count[5m])` by `status` |
| Embedding latency by batch size | `rate(embedding_generation_duration_seconds_sum[5m]) / rate(embedding_generation_duration_seconds_count[5m])` by `batch_size` |
| Faithfulness P50 | `histogram_quantile(0.50, rate(rag_evaluation_faithfulness_bucket[10m]))` |
| Answer relevance P50 | `histogram_quantile(0.50, rate(rag_evaluation_answer_relevance_bucket[10m]))` |

The `docker/local/docker-compose.yml` Grafana service gains two volume mounts:
```
- ./grafana/provisioning:/etc/grafana/provisioning
- ./grafana/dashboards:/var/lib/grafana/dashboards
```

### Structured Logging (prod profile)

`application-prod.yml` adds `logging.structured.format.console: ecs`. Spring Boot 3.4 built-in structured logging — no `logstash-logback-encoder` or `logback-spring.xml` needed. ECS (Elastic Common Schema) JSON format outputs `level`, `message`, `logger`, `traceId`, `spanId` as queryable fields in CloudWatch Logs Insights.

Local dev retains the existing human-readable pattern from `application.yml`.

### CloudWatch Alarms (Terraform, apply on AWS account)

New `infrastructure/terraform/modules/monitoring/` wired into `environments/prod/main.tf`:

| Alarm | Threshold | Rationale |
|---|---|---|
| ECS CPU > 80% | 2 consecutive 5-min periods | Sustained, not transient — avoids alerting on brief spikes |
| ECS running tasks < 1 | 1 period | Service is completely down — immediate alert |
| ALB 5xx > 10 in 5 min | 1 period | Degraded responses to users — actionable threshold |

SNS topic created; `sns_alarm_email` variable wires email notifications (optional).

---

## Files Created

### New Java

| File | Notes |
|---|---|
| `application/service/EvaluationService.java` | Async LLM-as-a-judge; `@Async("evaluationTaskExecutor")`; records faithfulness + relevance DistributionSummaries |

### Java Modified

| File | Change |
|---|---|
| `infrastructure/config/AsyncConfig.java` | Added `evaluationTaskExecutor` bean (virtual threads, `evaluation-` prefix) |
| `application/service/QueryService.java` | Added `EvaluationService`, `MeterRegistry`; `chat.query.duration` + `llm.completion.duration` timers; `similarity.search.results` summary; evaluation trigger |
| `application/service/EmbeddingService.java` | Added `MeterRegistry`; `embedding.generation.duration` + `ingestion.pipeline.duration` timers |
| `application/service/IngestionService.java` | Added `MeterRegistry`; `document.ingested` counter |

### Config Modified

| File | Change |
|---|---|
| `application.yml` | Added `app.evaluation.enabled: true`, `app.evaluation.sample-rate: 0.2` |
| `application-prod.yml` | Added `logging.structured.format.console: ecs` |

### Grafana (new)

| File | Notes |
|---|---|
| `docker/local/grafana/provisioning/datasources/prometheus.yml` | Auto-registers Prometheus datasource (uid: `prometheus`) |
| `docker/local/grafana/provisioning/dashboards/dashboard.yml` | Points Grafana at the dashboards directory |
| `docker/local/grafana/dashboards/knowledge-assistant.json` | 8-panel dashboard, schemaVersion 39, uid `knowledge-assistant` |
| `docker/local/docker-compose.yml` | Added provisioning volume mounts to Grafana service |

### Terraform (new/modified)

| File | Notes |
|---|---|
| `infrastructure/terraform/modules/monitoring/main.tf` | SNS topic + 3 CloudWatch alarms |
| `infrastructure/terraform/modules/monitoring/variables.tf` | |
| `infrastructure/terraform/modules/monitoring/outputs.tf` | `sns_topic_arn` |
| `infrastructure/terraform/modules/alb/outputs.tf` | Added `alb_arn_suffix` (needed by monitoring module) |
| `infrastructure/terraform/environments/prod/main.tf` | Added `module "monitoring"` |
| `infrastructure/terraform/environments/prod/variables.tf` | Added `sns_alarm_email` |
| `infrastructure/terraform/environments/prod/terraform.tfvars.example` | Documented `sns_alarm_email` |

### Tests

| File | Change |
|---|---|
| `QueryServiceTest.java` | Updated `setUp()` for new constructor; added `query_happyPath_recordsQueryTimer`, `query_noResults_recordsSearchOutcomeNotFound` |
| `EmbeddingServiceTest.java` | Updated `setUp()` for new constructor; added `handleTextExtracted_successPath_recordsEmbeddingTimer` |
| `EvaluationServiceTest.java` | New — 5 tests: below-sample-rate skips LLM, disabled skips LLM, happy path records both summaries, non-numeric response defaults to 0.5, LLM exception does not propagate |

---

## Acceptance Criteria

- [x] `mvn verify` green: 117 unit tests + 20 integration tests, 0 failures
- [x] ArchUnit passes (MeterRegistry in application layer only, not domain)
- [x] `/actuator/prometheus` returns `chat_query_duration_seconds`, `llm_completion_duration_seconds`, `similarity_search_results`, `embedding_generation_duration_seconds`, `ingestion_pipeline_duration_seconds`, `rag_evaluation_faithfulness`, `rag_evaluation_answer_relevance`
- [x] `docker compose --profile monitoring up -d` → Grafana at `localhost:3000` → Knowledge Assistant dashboard auto-provisions
- [x] Evaluation fires async — user response not delayed by judge LLM call
- [x] `app.evaluation.enabled: false` or `app.evaluation.sample-rate: 0.0` fully disables evaluation (no LLM calls)
- [ ] CloudWatch alarms active (requires AWS account — apply Terraform)

---

## Known Limitations (deferred)

- **OTel trace exporter**: `micrometer-tracing-bridge-otel` is on the classpath; trace IDs appear in logs. But no exporter is configured — traces aren't shipped to Zipkin/Jaeger/OTel Collector. Adding an exporter is Phase 10 scope.
- **Token usage** in `ChatMessage.promptTokens/completionTokens/modelUsed`: requires a richer `LlmPort` return type beyond `String`. Deferred carry-forward from Phase 7.
- **Full RAGAS evaluation**: context precision, context recall, and decomposed faithfulness require ground truth labels and multiple LLM calls per query. Upgrade path once an eval dataset is available.
- **Evaluation cost at scale**: 20% sample rate with the same provider as answering doubles cost for sampled requests. Configure `app.evaluation.sample-rate: 0.0` to disable in cost-sensitive environments or switch to a cheaper judge model in Phase 10.

---

## Git Commit Message

```
feat: Phase 9 — observability metrics, Grafana dashboard, LLM-as-a-judge evaluation

Metrics (Micrometer):
- QueryService: chat.query.duration (Timer, tag: provider),
  llm.completion.duration (Timer, tag: provider),
  similarity.search.results (DistributionSummary, tag: outcome=found/not_found)
- EmbeddingService: embedding.generation.duration (Timer, tag: batch_size),
  ingestion.pipeline.duration (Timer, tag: status=success/failure)
- IngestionService: document.ingested (Counter, tag: status=success/failure)

LLM-as-a-judge evaluation (EvaluationService):
- Async, sampled (app.evaluation.sample-rate=0.2 default)
- rag.evaluation.faithfulness and rag.evaluation.answer_relevance
  DistributionSummaries; clamped [0,1]; non-numeric fallback to 0.5
- AsyncConfig: evaluationTaskExecutor (virtual threads)

Grafana:
- Auto-provisioned datasource + dashboard via docker compose monitoring profile
- 8 panels: query rate, query P95, LLM P95, search outcomes, ingestion,
  embedding latency by batch, faithfulness P50, relevance P50

Config:
- application.yml: app.evaluation.enabled/sample-rate
- application-prod.yml: logging.structured.format.console: ecs (Spring Boot 3.4 built-in)

Terraform:
- modules/monitoring: SNS topic + 3 CloudWatch alarms (ECS CPU, task count, ALB 5xx)
- environments/prod: wires monitoring module

Tests: 117 unit (8 new) + 20 integration — BUILD SUCCESS

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```
