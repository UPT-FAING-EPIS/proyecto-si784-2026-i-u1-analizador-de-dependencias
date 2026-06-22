# Diccionario de Datos

## Propósito

Este documento define los datos principales usados por DepAnalyzer para analizar proyectos Maven, Gradle, npm y Python,
detectar versiones desactualizadas, consultar vulnerabilidades y generar reportes técnicos.

## Entidades del Sistema

| Entidad | Descripción | Origen | Atributos principales |
|---------|-------------|--------|-----------------------|
| Proyecto detectado | Proyecto analizado por la CLI. | `ProjectDetector` | `path`, `projectType`, `buildFile`, `ecosystem` |
| Dependencia | Componente declarado o resuelto desde el archivo de build. | Parsers Maven/Gradle/npm/Python | `groupId`, `artifactId`, `version`, `scope`, `ecosystem`, `section` |
| Repositorio | Fuente remota para consultar metadatos de versión. | Build files y defaults del ecosistema | `id`, `url`, `requiresAuthentication`, `trustedHost` |
| Vulnerabilidad | Hallazgo de seguridad asociado a una dependencia. | OSS Index o NVD | `id`, `title`, `description`, `cvssScore`, `severity`, `reference`, `source` |
| Severidad | Clasificación de criticidad de una vulnerabilidad. | CVSS / normalización interna | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`, `UNKNOWN` |
| Reporte de dependencias | Resultado consolidado del análisis. | `ProjectAnalyzer` | `projectName`, `upToDate`, `outdated`, `directVulnerable`, `transitiveVulnerable`, `dependencyTree` |
| Nodo de grafo | Nodo dentro del árbol/grafo de dependencias. | Resolución dinámica o parseo estático | `coordinate`, `children`, `isDirectDependency`, `parent`, `depth` |
| Cadena vulnerable | Ruta desde una dependencia directa hasta una transitiva vulnerable. | `core.graph` | `rootDependency`, `vulnerableDependency`, `path`, `vulnerabilities` |
| Fuente de vulnerabilidades | Modo de consulta para CVEs. | CLI y configuración | `AUTO`, `OSS_ONLY`, `NVD_ONLY`, `ossToken`, `nvdApiKey` |
| Sugerencia de actualización | Cambio recomendado para una dependencia. | `UpdatePlanner` | `id`, `dependency`, `currentVersion`, `targetVersion`, `targetType`, `reason` |
| Resultado de actualización | Resultado de aplicar o simular una sugerencia. | `BuildFileUpdater` | `suggestion`, `applied`, `note`, `backupPath` |
| Evento de telemetría | Registro anónimo de uso para mejorar la herramienta. | `TelemetryClient` | `eventType`, `feature`, `durationMs`, `errorType` |

## Catálogos

| Catálogo | Valores | Uso |
|----------|---------|-----|
| Tipo de proyecto | `MAVEN`, `GRADLE_GROOVY`, `GRADLE_KOTLIN`, `NPM`, `PYTHON_POETRY`, `PYTHON_REQUIREMENTS` | Selección de parser y updater. |
| Ecosistema | `MAVEN`, `NPM`, `PYPI` | Normalización de dependencias y fuente de CVEs. |
| Sección de dependencia | `DEPENDENCIES`, `DEPENDENCY_MANAGEMENT`, `DEV`, `TEST`, `RUNTIME` | Clasificación del origen de una dependencia. |
| Formato de salida | `console`, `json` | Renderizado humano o automatizable. |
| Expansión de árbol | `collapsed`, `critical`, `high`, `medium`, `all` | Control visual de árboles de dependencias. |

## Reglas de Calidad de Datos

- Las coordenadas de una dependencia deben conservar su ecosistema para evitar mezclar identificadores Maven, npm y PyPI.
- Si una versión no se puede resolver, se representa como `unknown` y no debe bloquear el reporte completo.
- Las credenciales solo se consideran válidas para hosts HTTPS incluidos en `DEPANALYZER_TRUSTED_CREDENTIAL_HOSTS`.
- Los reportes JSON deben ser parseables y mantener nombres de campos estables para integraciones CI/CD.
- Los errores de OSS Index o NVD deben registrarse como degradación controlada sin perder dependencias ya detectadas.
