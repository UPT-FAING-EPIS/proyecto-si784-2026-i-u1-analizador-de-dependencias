provider "github" {
  owner = var.github_owner
  token = var.github_token
}

resource "github_repository_environment" "pages" {
  repository  = var.repository_name
  environment = "github-pages"
}

resource "github_actions_variable" "public_site_url" {
  repository    = var.repository_name
  variable_name = "PUBLIC_SITE_URL"
  value         = var.public_site_url
}

resource "github_actions_variable" "minimum_coverage" {
  repository    = var.repository_name
  variable_name = "MINIMUM_COVERAGE"
  value         = tostring(var.minimum_coverage)
}
