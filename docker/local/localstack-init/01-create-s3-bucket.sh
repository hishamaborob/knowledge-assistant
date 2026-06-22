#!/bin/bash
# LocalStack initialization: create the S3 bucket used in local dev
aws --endpoint-url=http://localhost:4566 --region us-east-1 --no-sign-request \
    s3 mb s3://knowledge-assistant-docs-local
echo "S3 bucket created: knowledge-assistant-docs-local"
