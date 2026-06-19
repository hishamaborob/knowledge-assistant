# Infrastructure

ECS Fargate deployment on AWS, managed with Terraform.

## Prerequisites

- [Terraform](https://developer.hashicorp.com/terraform/install) >= 1.9
- [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html) v2
- An AWS account with admin access (for initial setup)
- A domain in Route53 (optional — only needed for HTTPS)

## Architecture

```
Internet → ALB (port 80/443) → ECS Fargate tasks (port 8080)
                                      ↓
                               RDS PostgreSQL 16 (pgvector)
                               S3 (document storage)
                               Secrets Manager (DB password, API keys)
```

**Modules:**
| Module    | What it creates |
|-----------|-----------------|
| `vpc`     | VPC, public/private subnets, NAT gateway |
| `security`| Security groups (ALB, ECS, RDS) |
| `ecr`     | Container registry with lifecycle policy |
| `rds`     | PostgreSQL 16 with random password (pgvector enabled via Flyway) |
| `s3`      | Document bucket with SSE + versioning |
| `secrets` | Secrets Manager secret (DB password, API keys) |
| `iam`     | ECS execution role + task role (least-privilege) |
| `alb`     | Application Load Balancer, target group, optional HTTPS |
| `ecs`     | ECS cluster, task definition, service, CPU autoscaling |

## Estimated cost (us-east-1)

| Resource | ~$/month |
|----------|----------|
| ECS Fargate 0.5 vCPU / 1 GB (1 task) | ~$15 |
| RDS db.t3.micro | ~$15 |
| NAT Gateway | ~$35 |
| ALB | ~$16 |
| S3 + Secrets Manager | < $1 |
| **Total** | **~$82** |

Teardown with `terraform destroy` stops all billing.

## Deploying

### Step 1 — Bootstrap Terraform state (one time)

```bash
cd infrastructure/terraform/bootstrap
terraform init
terraform apply
```

Note the `state_bucket_name` output. It should match the bucket name in
`environments/prod/backend.tf` (default: `knowledge-assistant-tfstate`).

### Step 2 — Configure variables

```bash
cd infrastructure/terraform/environments/prod
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your API keys and preferences
```

### Step 3 — Init and apply

```bash
terraform init   # downloads providers, connects to S3 backend
terraform plan   # review what will be created
terraform apply
```

After apply, note the outputs:
- `alb_dns_name` — the app URL (or your domain CNAME target)
- `ecr_repository_url` — set as `ECR_REGISTRY` in GitHub repo secrets

### Step 4 — Set up GitHub Actions OIDC (for CD workflow)

Instead of long-lived IAM access keys, the CD workflow uses OIDC token exchange.
Run this once in your AWS account:

```bash
# Create OIDC provider for GitHub Actions
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1

# Create the deploy IAM role (trust GitHub Actions OIDC)
# Replace YOUR_GITHUB_ORG/YOUR_REPO with your actual org/repo
aws iam create-role \
  --role-name knowledge-assistant-github-deploy \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Federated": "arn:aws:iam::ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"},
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {"token.actions.githubusercontent.com:aud": "sts.amazonaws.com"},
        "StringLike":   {"token.actions.githubusercontent.com:sub": "repo:YOUR_GITHUB_ORG/YOUR_REPO:ref:refs/heads/main"}
      }
    }]
  }'

# Attach ECR + ECS permissions to the deploy role
aws iam attach-role-policy --role-name knowledge-assistant-github-deploy \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryPowerUser
aws iam attach-role-policy --role-name knowledge-assistant-github-deploy \
  --policy-arn arn:aws:iam::aws:policy/AmazonECS_FullAccess
```

Then add to GitHub repo **Settings → Secrets → Actions**:
- `AWS_DEPLOY_ROLE_ARN` — the ARN of the deploy role above
- `AWS_ACCOUNT_ID` — your 12-digit account ID

### Step 5 — Push to deploy

Any push to `main` triggers `.github/workflows/cd.yml`:
1. Builds the Docker image from `docker/prod/Dockerfile`
2. Pushes to ECR with the commit SHA tag
3. Forces a new ECS deployment and waits for stability

## HTTPS setup (optional)

1. Set `enable_https = true` and `domain_name = "your.domain.com"` in `terraform.tfvars`
2. Run `terraform apply`
3. Read the `acm_validation_options` output and add the DNS CNAME records to Route53
4. Wait for ACM to validate (~2 minutes)
5. Add a CNAME from your domain to `alb_dns_name`

## Teardown

```bash
cd infrastructure/terraform/environments/prod
terraform destroy
```

The RDS instance takes a final snapshot before deletion (configured by `skip_final_snapshot = false`).
The bootstrap S3 bucket has `prevent_destroy = true` — delete it manually if needed:
```bash
aws s3 rb s3://knowledge-assistant-tfstate --force
```
