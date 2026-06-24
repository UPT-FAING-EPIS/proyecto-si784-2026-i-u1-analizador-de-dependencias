output "pages_environment" {
  description = "GitHub environment used by the Pages deployment."
  value       = github_repository_environment.pages.environment
}

output "public_site_url" {
  description = "Public evidence portal."
  value       = github_actions_variable.public_site_url.value
}

output "estimated_monthly_infrastructure_cost_usd" {
  description = "Estimated direct monthly cost for the declared public GitHub resources."
  value       = 0
}
