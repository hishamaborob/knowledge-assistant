terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_availability_zones" "available" {
  state = "available"
}

module "vpc" {
  source             = "../../modules/vpc"
  project_name       = var.project_name
  environment        = "prod"
  availability_zones = slice(data.aws_availability_zones.available.names, 0, 2)
}

module "security" {
  source       = "../../modules/security"
  project_name = var.project_name
  environment  = "prod"
  vpc_id       = module.vpc.vpc_id
}

module "ecr" {
  source       = "../../modules/ecr"
  project_name = var.project_name
  environment  = "prod"
}

module "rds" {
  source             = "../../modules/rds"
  project_name       = var.project_name
  environment        = "prod"
  private_subnet_ids = module.vpc.private_subnet_ids
  rds_sg_id          = module.security.rds_sg_id
  db_name            = var.db_name
  db_username        = var.db_username
  instance_class     = var.rds_instance_class
}

module "s3" {
  source       = "../../modules/s3"
  project_name = var.project_name
  environment  = "prod"
}

module "secrets" {
  source            = "../../modules/secrets"
  project_name      = var.project_name
  environment       = "prod"
  db_password       = module.rds.db_password
  openai_api_key    = var.openai_api_key
  anthropic_api_key = var.anthropic_api_key
}

module "iam" {
  source          = "../../modules/iam"
  project_name    = var.project_name
  environment     = "prod"
  secret_arn      = module.secrets.secret_arn
  docs_bucket_arn = module.s3.bucket_arn
}

module "alb" {
  source            = "../../modules/alb"
  project_name      = var.project_name
  environment       = "prod"
  vpc_id            = module.vpc.vpc_id
  alb_sg_id         = module.security.alb_sg_id
  public_subnet_ids = module.vpc.public_subnet_ids
  enable_https      = var.enable_https
  domain_name       = var.domain_name
}

module "ecs" {
  source             = "../../modules/ecs"
  project_name       = var.project_name
  environment        = "prod"
  aws_region         = var.aws_region
  private_subnet_ids = module.vpc.private_subnet_ids
  ecs_sg_id          = module.security.ecs_sg_id
  execution_role_arn = module.iam.ecs_execution_role_arn
  task_role_arn      = module.iam.ecs_task_role_arn
  ecr_repository_url = module.ecr.repository_url
  image_tag          = var.image_tag
  target_group_arn   = module.alb.target_group_arn
  db_endpoint        = module.rds.db_endpoint
  db_name            = var.db_name
  db_username        = var.db_username
  docs_bucket_name   = module.s3.bucket_name
  llm_provider       = var.llm_provider
}

module "monitoring" {
  source            = "../../modules/monitoring"
  project_name      = var.project_name
  environment       = "prod"
  ecs_cluster_name  = module.ecs.cluster_name
  ecs_service_name  = module.ecs.service_name
  alb_arn_suffix    = module.alb.alb_arn_suffix
  sns_alarm_email   = var.sns_alarm_email
}
