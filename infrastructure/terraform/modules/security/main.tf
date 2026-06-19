locals {
  tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# ALB: accept HTTPS (and HTTP for redirect) from the internet.
resource "aws_security_group" "alb" {
  name        = "${var.project_name}-${var.environment}-alb"
  description = "ALB inbound from internet"
  vpc_id      = var.vpc_id
  tags        = merge(local.tags, { Name = "${var.project_name}-${var.environment}-alb" })

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTP (redirected to HTTPS)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ECS tasks: accept only traffic from the ALB on the app port.
# Egress open so the task can reach ECR (pull), S3, Secrets Manager, and RDS.
resource "aws_security_group" "ecs" {
  name        = "${var.project_name}-${var.environment}-ecs"
  description = "ECS Fargate tasks — inbound from ALB only"
  vpc_id      = var.vpc_id
  tags        = merge(local.tags, { Name = "${var.project_name}-${var.environment}-ecs" })

  ingress {
    description     = "App port from ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# RDS: accept connections only from ECS tasks. No public access.
resource "aws_security_group" "rds" {
  name        = "${var.project_name}-${var.environment}-rds"
  description = "RDS PostgreSQL — inbound from ECS tasks only"
  vpc_id      = var.vpc_id
  tags        = merge(local.tags, { Name = "${var.project_name}-${var.environment}-rds" })

  ingress {
    description     = "PostgreSQL from ECS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }
}
