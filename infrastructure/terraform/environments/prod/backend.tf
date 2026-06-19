terraform {
  backend "s3" {
    # Created by infrastructure/terraform/bootstrap/main.tf — run that first.
    bucket         = "knowledge-assistant-tfstate"
    key            = "prod/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "knowledge-assistant-tfstate-lock"
    encrypt        = true
  }
}
