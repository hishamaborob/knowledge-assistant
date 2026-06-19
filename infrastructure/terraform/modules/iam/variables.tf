variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "secret_arn" {
  type        = string
  description = "ARN of the Secrets Manager secret the app reads at startup."
}

variable "docs_bucket_arn" {
  type        = string
  description = "ARN of the S3 bucket used for document storage."
}
