#!/bin/bash
# LocalStack initialization: create the Secrets Manager secret used in local dev.
# The Spring app reads this via spring.config.import: optional:aws-secretsmanager:/knowledge-assistant/local
# Keys match the ${...} expressions in application.yml.
# Replace placeholder API keys with real values for end-to-end testing,
# or leave them — the "optional:" prefix means the app falls back to application-local.yml defaults.

aws --endpoint-url=http://localhost:4566 --region us-east-1 --no-sign-request \
    secretsmanager create-secret \
    --name /knowledge-assistant/local \
    --secret-string '{
      "DB_PASSWORD":       "ka_password",
      "OPENAI_API_KEY":    "sk-placeholder-replace-with-real-key",
      "ANTHROPIC_API_KEY": "sk-ant-placeholder-replace-with-real-key"
    }'

echo "Secrets Manager secret created: /knowledge-assistant/local"
