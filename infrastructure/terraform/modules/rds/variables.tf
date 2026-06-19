variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "rds_sg_id" {
  type = string
}

variable "db_name" {
  type    = string
  default = "knowledge_assistant"
}

variable "db_username" {
  type    = string
  default = "ka_user"
}

variable "instance_class" {
  type    = string
  default = "db.t3.micro"
}
