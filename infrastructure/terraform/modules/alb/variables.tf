variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "alb_sg_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "enable_https" {
  type        = bool
  default     = false
  description = "Set to true when you have a domain in Route53 and provide domain_name."
}

variable "domain_name" {
  type        = string
  default     = ""
  description = "e.g. knowledge-assistant.example.com — required when enable_https = true."
}
