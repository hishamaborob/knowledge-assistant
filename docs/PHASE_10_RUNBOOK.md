# Production Runbook — Knowledge Assistant

## Deployment

### Prerequisites
- AWS account with Terraform state backend bootstrapped (`infrastructure/terraform/bootstrap/`)
- GitHub OIDC role configured (see `infrastructure/README.md`)
- GitHub secrets: `AWS_DEPLOY_ROLE_ARN`

### First-time deployment
```bash
cd infrastructure/terraform/environments/prod
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars: openai_api_key, sns_alarm_email, etc.

terraform init
terraform plan   # Review — check estimated costs
terraform apply
```

### Updating the API key

After `terraform apply`, add `API_KEYS` to the Secrets Manager secret:
```bash
aws secretsmanager put-secret-value \
  --secret-id /knowledge-assistant/prod \
  --secret-string "$(aws secretsmanager get-secret-value \
    --secret-id /knowledge-assistant/prod \
    --query SecretString --output text | \
    jq --arg k "$(openssl rand -hex 32)" '. + {API_KEYS: $k}')"
```

Then force a new ECS deployment to pick up the new secret:
```bash
aws ecs update-service \
  --cluster knowledge-assistant-prod \
  --service knowledge-assistant-prod \
  --force-new-deployment
```

### Rotating an API key (zero downtime)

1. Generate a new key: `openssl rand -hex 32`
2. Add the new key to `API_KEYS` (comma-separated alongside the old key)
3. Update Secrets Manager and force ECS redeploy
4. Distribute the new key to all callers
5. Once all callers are migrated, remove the old key from `API_KEYS`
6. Update Secrets Manager and force ECS redeploy again

### CD pipeline (ongoing deployments)

Every push to `main` triggers `.github/workflows/cd.yml`:
1. Docker image built from `docker/prod/Dockerfile`
2. Pushed to ECR with commit SHA tag + `latest`
3. `aws ecs update-service --force-new-deployment`
4. `aws ecs wait services-stable`

The ECS service uses `deployment_minimum_healthy_percent=100` — a new task starts before the old one drains, ensuring zero-downtime deploys.

---

## Scaling

### ECS task sizing

| Load | vCPU | Memory | Notes |
|---|---|---|---|
| Development / low | 0.5 | 1 GB | Default Terraform config |
| Medium (50 req/min) | 1.0 | 2 GB | Adjust `var.cpu` and `var.memory` |
| High (200+ req/min) | 2.0 | 4 GB | Also increase HikariCP `maximum-pool-size` |

Autoscaling is configured for 1–4 tasks at CPU >70%. The `aws_appautoscaling_policy` handles horizontal scaling automatically.

### RDS sizing

Start with `db.t3.micro`. Move to `db.t3.small` or `db.t3.medium` when:
- Connection wait times appear in HikariCP metrics
- CPU > 70% sustained during peak load
- `pg_stat_activity` shows many idle-in-transaction connections

Connection pool formula: `(vCPU × 2) + 1 effective spindle`. For `db.t3.medium` (2 vCPU): `10` connections per app instance. Current Hikari config: `maximum-pool-size: 10` — correct for this instance class.

### pgvector index

The HNSW index (`m=16, ef_construction=64`) handles up to ~1M vectors well. Beyond 1M:
- Increase `ef_construction` to 128 for better recall
- Consider partitioning `document_chunks` by `document_id` range

Check index size:
```sql
SELECT pg_size_pretty(pg_relation_size('document_chunks_embedding_idx'));
```

---

## Common Failures and Remediation

### App won't start — SM secret missing

**Symptom:** `BeanCreationException: Failed to instantiate SecretsManagerClient` or startup timeout.

**Cause:** `spring.config.import: aws-secretsmanager:/knowledge-assistant/prod` can't reach SM, or the secret doesn't exist.

**Fix:**
```bash
# Verify secret exists
aws secretsmanager describe-secret --secret-id /knowledge-assistant/prod

# Verify ECS task role has GetSecretValue
aws iam simulate-principal-policy \
  --policy-source-arn <task-role-arn> \
  --action-names secretsmanager:GetSecretValue \
  --resource-arns <secret-arn>
```

### Circuit breaker open — LLM unavailable

**Symptom:** All `/queries` and `/sessions/*/messages` return 200 with `"The AI service is temporarily unavailable. Please try again in a moment."` in the answer field.

**Cause:** The `llm` circuit breaker opened after 5 failures in 10 seconds (rate limit exceeded, provider outage, network issue).

**Checks:**
1. CloudWatch Logs: `docker logs` or Logs Insights query:
   ```
   fields @message | filter @message like /circuit breaker/
   ```
2. Check provider status page (OpenAI, Anthropic)
3. Check API key validity: `curl -H "Authorization: Bearer $OPENAI_API_KEY" https://api.openai.com/v1/models`

**Recovery:** Circuit breaker auto-transitions to half-open after 30s and retries. No manual intervention needed unless the provider is down for >30 minutes.

### 429 rate limit errors

**Symptom:** Clients receive `429 Too Many Requests`.

**Cause:** >20 requests in 10 seconds to `/queries` or `/sessions/*/messages`.

**Fix (short-term):** Increase `resilience4j.ratelimiter.instances.queryApi.limit-for-period` in `application.yml` and redeploy.

**Fix (long-term):** Implement per-API-key rate limiting using the key identity from `SecurityContextHolder`.

### Flyway migration fails on startup

**Symptom:** App crashes with `FlywayException: Validate failed`.

**Cause:** A migration was applied in the wrong order, or a checksum mismatch.

**Recovery:**
```bash
# Connect to RDS via bastion or ECS exec
aws ecs execute-command \
  --cluster knowledge-assistant-prod \
  --task <task-id> \
  --container knowledge-assistant \
  --command "/bin/sh" \
  --interactive

# Inside container — check Flyway schema history
# Then repair if a migration was applied partially
```

Never edit an already-applied migration. Always create a new migration file.

### S3 document upload fails

**Symptom:** `POST /documents` returns 500, logs show `S3Exception`.

**Checks:**
1. ECS task role has `s3:PutObject` on the documents bucket
2. Bucket exists: `aws s3 ls s3://knowledge-assistant-docs-prod-<account-id>`
3. VPC routing: ECS tasks in private subnets reach S3 via NAT Gateway or VPC endpoint

---

## Monitoring and Alerts

### CloudWatch alarms (auto-created by Terraform)

| Alarm | Condition | Action |
|---|---|---|
| ECS CPU high | >80% for 2×5min periods | SNS email |
| Service down | Running tasks <1 | SNS email (immediate) |
| ALB 5xx errors | >10 in 5 minutes | SNS email |

### Grafana dashboard (local or prod Prometheus)

Key panels to watch:
- **Faithfulness P50 < 0.6**: retrieval quality degraded — check if documents are current
- **LLM P95 > 5s**: provider is slow — consider switching `app.llm.provider`
- **Search outcome "not_found" spike**: similarity threshold too high, or embeddings not indexed for new documents

### CloudWatch Logs Insights queries

**High error rate:**
```
fields @timestamp, @message
| filter @message like /ERROR/
| stats count(*) as errorCount by bin(5m)
| sort @timestamp desc
```

**Slow queries (>3s):**
```
fields @timestamp, @message
| filter @message like /query.*durationMs/ and @message like /[3-9][0-9]{3}|[1-9][0-9]{4}/
| sort @timestamp desc
| limit 20
```

---

## RDS Backup and Restore

### Automated backups

RDS automated backups are enabled with 7-day retention (`backup_retention_period = 7` in Terraform). Point-in-time recovery is available within that window.

### Manual snapshot before risky migrations

```bash
aws rds create-db-snapshot \
  --db-instance-identifier knowledge-assistant-prod \
  --db-snapshot-identifier knowledge-assistant-prod-pre-migration-$(date +%Y%m%d)
```

### Restore from snapshot

```bash
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier knowledge-assistant-prod-restored \
  --db-snapshot-identifier <snapshot-id>
```

Update `DB_URL` in Secrets Manager to point at the restored instance, then force an ECS redeploy.

---

## Secret Rotation

### Rotating the DB password

1. Generate a new password: `openssl rand -base64 32 | tr -d /=+`
2. Update RDS: `aws rds modify-db-instance --db-instance-identifier knowledge-assistant-prod --master-user-password <new-password> --apply-immediately`
3. Update SM secret: set `DB_PASSWORD` to the new password
4. Force ECS redeploy (Spring Cloud AWS reads SM at startup)

### Rotating LLM API keys

1. Generate a new key on the provider's console
2. Add new key to SM secret (`OPENAI_API_KEY`)
3. Force ECS redeploy
4. Revoke old key on the provider's console

---

## Teardown

```bash
cd infrastructure/terraform/environments/prod
terraform destroy
```

RDS takes a final snapshot before deletion (controlled by `skip_final_snapshot = false`). The final snapshot is named `knowledge-assistant-prod-final` and incurs storage costs until manually deleted.

The Terraform state S3 bucket has `prevent_destroy = true` — delete manually:
```bash
aws s3 rb s3://knowledge-assistant-tfstate --force
aws dynamodb delete-table --table-name knowledge-assistant-tfstate-lock
```

---

## Upgrade Path: JWT Authentication

To upgrade from API key auth to JWT with an external IdP:

1. Stand up an IdP (Keycloak, Auth0, or AWS Cognito)
2. Add to `pom.xml`: `spring-boot-starter-oauth2-resource-server`
3. Add to `application-prod.yml`:
   ```yaml
   spring:
     security:
       oauth2:
         resourceserver:
           jwt:
             jwk-set-uri: https://<your-idp>/.well-known/jwks.json
   ```
4. Replace `ApiKeyAuthFilter` with Spring Security's built-in JWT filter (remove the custom filter; Spring auto-configures JWT validation when the above property is set)
5. Remove `app.security.api-keys` and `API_KEYS` from Secrets Manager

The `SecurityConfig` permit rules (`/actuator/**`, `/swagger-ui/**`) carry over unchanged.
