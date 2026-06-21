output "alb_dns_name" {
  value = aws_lb.main.dns_name
}

output "alb_arn_suffix" {
  value = aws_lb.main.arn_suffix
}

output "target_group_arn" {
  value = aws_lb_target_group.app.arn
}

output "acm_certificate_arn" {
  value = var.enable_https ? aws_acm_certificate.main[0].arn : null
}

# DNS validation records — add these to Route53 to complete ACM cert validation.
output "acm_validation_options" {
  value = var.enable_https ? aws_acm_certificate.main[0].domain_validation_options : null
}
