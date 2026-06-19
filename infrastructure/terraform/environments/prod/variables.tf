variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "project_name" {
  type    = string
  default = "knowledge-assistant"
}

variable "db_name" {
  type    = string
  default = "knowledge_assistant"
}

variable "db_username" {
  type    = string
  default = "ka_user"
}

variable "rds_instance_class" {
  type    = string
  default = "db.t3.micro"
}

variable "llm_provider" {
  type    = string
  default = "openai"
}

variable "image_tag" {
  type    = string
  default = "latest"
}

variable "enable_https" {
  type    = bool
  default = false
}

variable "domain_name" {
  type    = string
  default = ""
}

variable "openai_api_key" {
  type      = string
  sensitive = true
}

variable "anthropic_api_key" {
  type      = string
  sensitive = true
  default   = ""
}
