output "alb_dns_name" {
  description = "Point your domain CNAME here, or use this directly to reach the app."
  value       = module.alb.alb_dns_name
}

output "ecr_repository_url" {
  description = "Set this as ECR_REGISTRY in GitHub Actions secrets."
  value       = module.ecr.repository_url
}

output "ecs_cluster_name" {
  value = module.ecs.cluster_name
}

output "ecs_service_name" {
  value = module.ecs.service_name
}

output "cloudwatch_log_group" {
  value = module.ecs.log_group_name
}

output "docs_bucket_name" {
  value = module.s3.bucket_name
}

output "acm_validation_options" {
  description = "DNS records to add in Route53 to validate the ACM certificate."
  value       = module.alb.acm_validation_options
}
