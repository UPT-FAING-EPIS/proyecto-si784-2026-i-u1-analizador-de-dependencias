# Infraestructura Terraform

Este módulo declara la infraestructura de evidencia pública de DepAnalyzer:

- entorno protegido `github-pages`;
- variable `PUBLIC_SITE_URL`;
- variable `MINIMUM_COVERAGE`, con un mínimo permitido de 70.

## Uso local

```powershell
$env:TF_VAR_github_token="token-con-permisos-de-administracion"
terraform init
terraform fmt -check
terraform validate
terraform plan
terraform apply
```

El token no debe guardarse en archivos `.tfvars`. El workflow utiliza `TERRAFORM_GITHUB_TOKEN` cuando está configurado
y, en su defecto, intenta usar el token temporal de GitHub Actions.

## Estado y repetibilidad

El workflow importa los recursos existentes antes del plan. Esto permite reconstruir un estado efímero en el runner sin
publicar credenciales ni archivos de estado en el repositorio.

## Estimación de costos

| Recurso | Cantidad | Precio directo mensual |
|---------|----------|-------------------------|
| GitHub environment | 1 | USD 0.00 |
| GitHub Actions variables | 2 | USD 0.00 |
| GitHub Pages para repositorio público | 1 | USD 0.00 |
| **Total infraestructura declarada** | | **USD 0.00** |

Los minutos de ejecución, energía e internet se analizan por separado en FD01.
