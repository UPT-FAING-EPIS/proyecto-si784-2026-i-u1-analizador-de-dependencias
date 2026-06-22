# FD05 - Informe de Proyecto

## 1. Resumen Ejecutivo

DepAnalyzer es una aplicación CLI/TUI desarrollada en Kotlin para analizar dependencias de proyectos Maven, Gradle, npm
y Python. El sistema identifica dependencias desactualizadas, consulta vulnerabilidades conocidas con OSS Index y NVD, y
genera reportes legibles o JSON para uso manual y CI/CD.

## 2. Producto Entregado

| Componente | Estado | Evidencia |
|------------|--------|-----------|
| CLI `analyze` | Implementado | `src/main/kotlin/com/depanalyzer/cli/DepAnalyzerCli.kt` |
| CLI `tui` | Implementado | `src/main/kotlin/com/depanalyzer/tui/AnalyzeTuiApp.kt` |
| CLI `update` | Implementado | `src/main/kotlin/com/depanalyzer/cli/UpdateCommand.kt` |
| Parsers Maven/Gradle | Implementado | `src/main/kotlin/com/depanalyzer/parser` |
| Parsers npm/Python | Implementado | `src/main/kotlin/com/depanalyzer/parser/npm`, `parser/python` |
| Integración OSS Index/NVD | Implementado | `src/main/kotlin/com/depanalyzer/repository` |
| Reportes JSON/consola | Implementado | `src/main/kotlin/com/depanalyzer/report` |
| Servidor MCP | Implementado | `integrations/mcp` |
| Documentación FD | En cierre | `docs/` |
| GitHub Pages de evidencias | En cierre | `.github/workflows/pages.yml` |

## 3. Alcance Final

El alcance final incluye análisis multi-ecosistema, clasificación de vulnerabilidades directas y transitivas, reportes
JSON, interfaz TUI, actualización guiada con backup, documentación de arquitectura y publicación de evidencias de
calidad en GitHub Pages.

Quedan fuera de alcance una interfaz web dedicada, remediación sin confirmación y sustitución total de plataformas SCA
empresariales.

## 4. Evidencias de Calidad

Las evidencias de calidad se publican desde GitHub Pages:

- [Reportes de pruebas](./reports/)
- [Documentación autogenerada](./api-docs/)
- [FD04 - Arquitectura](./FD04-EPIS-Informe%20Arquitectura%20de%20Software.html)

## 5. Gestión del Proyecto

| Aspecto | Resultado |
|---------|-----------|
| Metodología | Desarrollo incremental con validación por pruebas. |
| Control de versiones | GitHub con workflows de CI. |
| Calidad | Pruebas unitarias, integración, mutación y análisis estático. |
| Seguridad | Consulta de CVEs y manejo controlado de credenciales. |
| Documentación | FD01-FD05, diccionario de datos, estándar de programación y README. |

## 6. Conclusiones

1. El producto cumple el objetivo principal de analizar dependencias y vulnerabilidades de forma local y automatizable.
2. La arquitectura modular permite extender parsers, fuentes de vulnerabilidades y formatos de reporte.
3. La publicación en GitHub Pages centraliza la evidencia documental y de pruebas solicitada para evaluación.
4. Los reportes automatizados fortalecen trazabilidad entre requerimientos, implementación y verificación.
