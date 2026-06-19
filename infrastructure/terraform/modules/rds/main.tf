locals {
  tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# Random password: no special characters to avoid JDBC URL encoding issues.
resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-${var.environment}"
  subnet_ids = var.private_subnet_ids
  tags       = local.tags
}

# RDS PG16 supports pgvector as a built-in extension.
# No custom parameter group settings needed — Flyway's V1 migration runs
# "CREATE EXTENSION IF NOT EXISTS vector" at first app boot.
resource "aws_db_parameter_group" "main" {
  name   = "${var.project_name}-${var.environment}-pg16"
  family = "postgres16"
  tags   = local.tags
}

resource "aws_db_instance" "main" {
  identifier        = "${var.project_name}-${var.environment}"
  engine            = "postgres"
  engine_version    = "16.4"
  instance_class    = var.instance_class
  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [var.rds_sg_id]
  parameter_group_name   = aws_db_parameter_group.main.name

  multi_az            = false  # Cost: ~$30/month saved. Add for production if needed.
  publicly_accessible = false

  backup_retention_period   = 7
  performance_insights_enabled = true

  deletion_protection       = false  # Set to true for a real production deployment.
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project_name}-${var.environment}-final"

  tags = local.tags
}
