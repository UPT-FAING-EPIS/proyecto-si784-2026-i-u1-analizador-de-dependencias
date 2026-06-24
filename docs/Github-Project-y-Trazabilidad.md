# GitHub Project y Trazabilidad

## Regla para las tareas

Toda tarea incorporada al GitHub Project debe incluir:

1. Historia `Como / Quiero / Para`.
2. Criterios de aceptación identificados.
3. Dos escenarios `Dado / Cuando / Entonces` por criterio.
4. Evidencia de prueba o reporte.
5. Versión objetivo del roadmap.

El formulario `.github/ISSUE_TEMPLATE/user-story.yml` hace obligatorios estos campos para nuevas tareas.

## Flujo del tablero

| Estado | Condición |
|--------|-----------|
| Backlog | Historia redactada y versión asignada |
| Ready | Criterios y escenarios revisados |
| In progress | Implementación iniciada |
| In review | Pruebas y evidencias disponibles |
| Done | Criterios cumplidos, workflow exitoso y documentación actualizada |

## Verificación remota pendiente

Para consultar y modificar GitHub Projects desde CLI, la autenticación necesita los alcances `read:project` y `project`.
La comprobación se realiza con:

```bash
gh auth refresh -s read:project -s project
gh project list --owner UPT-FAING-EPIS
```

Esta configuración no sustituye la evidencia del tablero remoto; documenta el procedimiento y evita que nuevas tareas se
creen sin la estructura exigida.

## Secretos de automatización administrativa

- `TERRAFORM_GITHUB_TOKEN`: token con administración del repositorio para aplicar el plan Terraform.
- `WIKI_TOKEN`: token con escritura para sincronizar las páginas versionadas después de crear la primera página Wiki.

Sin estos secretos, los workflows validan sus fuentes y registran la configuración pendiente sin marcar la entrega como
fallida.
