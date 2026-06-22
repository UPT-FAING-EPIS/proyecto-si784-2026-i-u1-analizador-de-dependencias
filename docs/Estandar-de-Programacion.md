# Estándar de Programación

## Propósito

Este estándar define las reglas de desarrollo para DepAnalyzer. Su objetivo es mantener consistencia entre código,
pruebas, documentación y automatización de calidad.

## Kotlin y Gradle

| Regla | Criterio |
|-------|----------|
| Estilo Kotlin | Usar `kotlin.code.style=official`. |
| Nombres de clases | `PascalCase`, por ejemplo `ProjectAnalyzer`. |
| Nombres de funciones | `camelCase`, por ejemplo `getVulnerabilities`. |
| Constantes | `UPPER_SNAKE_CASE`, por ejemplo `BATCH_SIZE`. |
| Paquetes | Dominio reverso bajo `com.depanalyzer`. |
| Tipado | Preferir tipos no-nullable y usar `require` para precondiciones. |
| Inyección | Preferir constructor injection para clientes externos y colaboradores. |

## Organización del Código

| Módulo | Responsabilidad |
|--------|-----------------|
| `cli` | Comandos `analyze`, `tui`, `update` y opciones de usuario. |
| `core` | Orquestación del análisis y consolidación de resultados. |
| `parser` | Lectura de Maven, Gradle, npm y Python. |
| `repository` | Integración con repositorios, OSS Index y NVD. |
| `report` | Modelos y renderizado de reportes. |
| `update` | Planificación y aplicación segura de actualizaciones. |
| `tui` | Interfaz interactiva de terminal. |
| `security` | Reglas para credenciales y destinos confiables. |

## Manejo de Errores

- Usar `try/catch` en operaciones de archivos, red y ejecución de procesos externos.
- Reportar errores operativos en stderr con mensajes claros.
- Mantener degradación controlada ante fallas de OSS Index, NVD, Maven o Gradle.
- No interrumpir todo el análisis si una fuente externa falla y existe información parcial útil.

## Seguridad

- No registrar tokens, credenciales ni cabeceras sensibles.
- Leer `OSS_INDEX_TOKEN` y `NVD_API_KEY` desde variables de entorno o flags explícitos.
- Enviar credenciales de repositorios solo por HTTPS y solo a hosts confiables.
- No modificar archivos de build sin confirmación del usuario en flujos interactivos.
- Crear backup antes de aplicar actualizaciones.

## Pruebas

| Tipo | Ubicación / Evidencia |
|------|------------------------|
| Unitarias | `src/test/kotlin/com/depanalyzer/**` |
| Integración | `src/test/kotlin/com/depanalyzer/integration/**` |
| Interfaz CLI/TUI | Tests de `cli` y `tui` |
| Mutación | Reporte PIT en `build/reports/pitest` |
| MCP | `npm test` en `integrations/mcp` |

## Documentación

- Mantener `README.md` como guía operativa principal.
- Mantener documentos FD en `docs/` y publicarlos en GitHub Pages.
- Actualizar FD04 cuando cambien arquitectura, reportes de calidad o evidencia de pruebas.
- Generar documentación técnica con Dokka y publicarla en `/api-docs/`.

## Git

- No ejecutar `git add`, `git commit` ni `git push` sin autorización explícita.
- Usar Conventional Commits cuando el usuario autorice un commit.
- No incluir artefactos locales pesados como `node_modules`, `build` o paquetes temporales.
