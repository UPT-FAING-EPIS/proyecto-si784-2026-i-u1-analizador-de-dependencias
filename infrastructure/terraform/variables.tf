variable "github_token" {
  description = "GitHub token used by the provider. Supply it through TF_VAR_github_token."
  type        = string
  sensitive   = true
}

variable "github_owner" {
  description = "GitHub organization that owns the repository."
  type        = string
  default     = "UPT-FAING-EPIS"
}

variable "repository_name" {
  description = "Repository managed by this infrastructure module."
  type        = string
  default     = "proyecto-si784-2026-i-u1-analizador-de-dependencias"
}

variable "public_site_url" {
  description = "Public GitHub Pages URL."
  type        = string
  default     = "https://upt-faing-epis.github.io/proyecto-si784-2026-i-u1-analizador-de-dependencias/"
}

variable "minimum_coverage" {
  description = "Required line coverage percentage."
  type        = number
  default     = 70

  validation {
    condition     = var.minimum_coverage >= 70 && var.minimum_coverage <= 100
    error_message = "minimum_coverage must be between 70 and 100."
  }
}
