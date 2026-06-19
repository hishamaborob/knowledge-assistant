locals {
  tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }
  name = "${var.project_name}-${var.environment}"
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${local.name}"
  retention_in_days = 30
  tags              = local.tags
}

resource "aws_ecs_cluster" "main" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = local.tags
}

resource "aws_ecs_task_definition" "app" {
  family                   = local.name
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = var.execution_role_arn
  task_role_arn            = var.task_role_arn

  container_definitions = jsonencode([{
    name      = var.project_name
    image     = "${var.ecr_repository_url}:${var.image_tag}"
    essential = true

    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]

    # Non-sensitive config as plaintext env vars; secrets injected by Spring Cloud AWS
    # from Secrets Manager (DB_PASSWORD, OPENAI_API_KEY, ANTHROPIC_API_KEY).
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = var.environment },
      { name = "APP_ENV",               value = var.environment },
      { name = "AWS_REGION",            value = var.aws_region },
      { name = "DB_URL",               value = "jdbc:postgresql://${var.db_endpoint}:5432/${var.db_name}" },
      { name = "DB_USERNAME",           value = var.db_username },
      { name = "S3_BUCKET_NAME",        value = var.docs_bucket_name },
      { name = "LLM_PROVIDER",          value = var.llm_provider },
      { name = "EMBEDDING_PROVIDER",    value = "openai" },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.app.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }

    # start-period covers Spring Boot startup + Flyway migrations + SM secret fetch.
    healthCheck = {
      command     = ["CMD-SHELL", "wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1"]
      interval    = 30
      timeout     = 10
      retries     = 3
      startPeriod = 90
    }
  }])

  tags = local.tags
}

resource "aws_ecs_service" "app" {
  name            = local.name
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.ecs_sg_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = var.project_name
    container_port   = 8080
  }

  # 100/200: one new task starts before the old one is drained — zero downtime deploys.
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  # CI/CD updates the task definition image; ignore Terraform drift on this attribute.
  lifecycle {
    ignore_changes = [task_definition]
  }

  tags = local.tags
}

# ---------------------------------------------------------------------------
# Autoscaling: 1–4 tasks, scale out when CPU > 70% for 1 minute.
# Scale-in cooldown (300s) prevents thrashing after a traffic spike subsides.
# ---------------------------------------------------------------------------
resource "aws_appautoscaling_target" "ecs" {
  max_capacity       = 4
  min_capacity       = 1
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.app.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "${local.name}-cpu-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 70.0
    scale_out_cooldown = 60
    scale_in_cooldown  = 300
  }
}
