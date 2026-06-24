<center>

![Logo UPT](./media/logo-upt.png)

**UNIVERSIDAD PRIVADA DE TACNA**

**FACULTAD DE INGENIERÍA**

**Escuela Profesional de Ingeniería de Sistemas**

**Estándar de Programación**

**Sistema Analizador de Dependencias Multi-Lenguaje (DepAnalyzer)**

Curso: *Calidad y Pruebas de Software*

Docente: *Patrick Cuadros Quiroga*

Integrantes:

***Carbajal Vargas, Andre Alejandro (2023077287)***

***Yupa Gómez, Fátima Sofía (2023076618)***

**Tacna - Perú**

***2026***

</center>

<div style="page-break-after: always; visibility: hidden">\pagebreak</div>

Sistema *Analizador de Dependencias Multi-Lenguaje (DepAnalyzer)*

Estándar de Programación

Versión *1.0*

| CONTROL DE VERSIONES |           |              |               |            |                                      |
|:--------------------:|:----------|:-------------|:--------------|:-----------|:-------------------------------------|
|       Versión        | Hecha por | Revisada por | Aprobada por  | Fecha      | Motivo                               |
|         1.0          | ACV, FYG  | ACV, FYG     | P. Cuadros Q. | 2026-06-23 | Versión inicial del estándar         |

# ÍNDICE GENERAL

1. [Introducción](#1-introducción)
2. [Principios Generales](#2-principios-generales)
3. [Organización del Código](#3-organización-del-código)
4. [Convenciones Kotlin](#4-convenciones-kotlin)
5. [Manejo de Errores y Recursos](#5-manejo-de-errores-y-recursos)
6. [Seguridad](#6-seguridad)
7. [Pruebas](#7-pruebas)
8. [Documentación](#8-documentación)
9. [Git y Revisión de Cambios](#9-git-y-revisión-de-cambios)
10. [Automatización y Criterios de Aceptación](#10-automatización-y-criterios-de-aceptación)

<div style="page-break-after: always; visibility: hidden">\pagebreak</div>

# 1. Introducción

## 1.1 Propósito

Este estándar establece las reglas de desarrollo de DepAnalyzer para mantener consistencia entre código, pruebas,
documentación y automatización. Sus disposiciones se aplican a nuevas funcionalidades, correcciones, refactorizaciones y
revisiones de código.

## 1.2 Alcance

El estándar cubre la aplicación Kotlin, scripts de construcción, integración MCP, workflows, pruebas y documentos
técnicos. Cuando una herramienta aplique reglas más estrictas, se debe adoptar la regla que proporcione mayor claridad y
seguridad sin contradecir la arquitectura del proyecto.

## 1.3 Referencias

- Kotlin Coding Conventions y `kotlin.code.style=official`.
- Requerimientos definidos en FD03.
- Arquitectura y atributos de calidad definidos en FD04.
- Instrucciones operativas del repositorio en `AGENTS.md`.
- Reglas de construcción declaradas en `build.gradle.kts`.

# 2. Principios Generales

1. Priorizar legibilidad y comportamiento explícito sobre soluciones ingeniosas.
2. Mantener cada módulo enfocado en una responsabilidad.
3. Modelar estados válidos mediante tipos y enumeraciones.
4. Validar entradas en los límites: CLI, archivos, procesos y APIs.
5. Conservar degradación controlada cuando exista información parcial útil.
6. Evitar registrar o persistir secretos.
7. Acompañar cambios de comportamiento con pruebas.
8. Mantener documentación y código sincronizados.
9. Evitar refactorizaciones no relacionadas dentro de una corrección puntual.
10. Favorecer funciones pequeñas y nombres que expresen intención.

# 3. Organización del Código

| Paquete / Módulo | Responsabilidad permitida |
|------------------|----------------------------|
| `cli` | Comandos, opciones, validación de entrada y códigos de salida |
| `core` | Orquestación del análisis y reglas transversales |
| `core.graph` | Grafo, nodos, recorridos y cadenas vulnerables |
| `parser` | Detección y lectura de manifiestos o lockfiles |
| `repository` | APIs, repositorios de paquetes, autenticación y reintentos |
| `report` | Modelos de salida, JSON, consola y árboles |
| `update` | Planificación, respaldo y modificación controlada |
| `tui` | Estado, layout, interacción y capacidades de terminal |
| `security` | Políticas para credenciales y destinos confiables |
| `telemetry` | Eventos anónimos y control de envío |
| `integrations/mcp` | Exposición controlada para clientes MCP |

## 3.1 Dependencias entre Módulos

- La CLI puede invocar el núcleo, pero no debe implementar parseo ni clientes HTTP.
- Los parsers no deben renderizar salida de consola.
- Los clientes de repositorio no deben decidir cómo se presenta un hallazgo.
- Los modelos de reporte no deben depender de detalles visuales de la TUI.
- Los actualizadores deben modificar únicamente el formato que conocen.
- Las integraciones externas deben consumir interfaces públicas y salida estructurada.

## 3.2 Estructura de Archivos

- Producción: `src/main/kotlin/com/depanalyzer/**`.
- Pruebas: `src/test/kotlin/com/depanalyzer/**`.
- Fixtures: `src/test/resources/**`.
- Documentación: `docs/**`.
- Workflows: `.github/workflows/**`.
- Integraciones independientes: `integrations/<nombre>/**`.

# 4. Convenciones Kotlin

## 4.1 Nombres

| Elemento | Convención | Ejemplo |
|----------|------------|---------|
| Clase, interfaz, enum | `PascalCase` | `ProjectAnalyzer` |
| Función y propiedad | `camelCase` | `getVulnerabilities` |
| Constante | `UPPER_SNAKE_CASE` | `MAX_RETRIES` |
| Paquete | Minúsculas, dominio reverso | `com.depanalyzer.parser` |
| Archivo | Nombre del tipo principal | `DependencyReport.kt` |
| Test | Tipo o comportamiento probado + `Test` | `OssIndexClientTest` |

Los nombres deben describir el dominio. Se deben evitar abreviaturas ambiguas, sufijos genéricos como `Manager` cuando
exista una responsabilidad más precisa y nombres que repitan innecesariamente el paquete.

## 4.2 Formato e Imports

- Aplicar el estilo oficial de Kotlin.
- Utilizar cuatro espacios y no tabulaciones.
- Mantener una declaración por línea cuando mejore la lectura.
- Organizar imports automáticamente y eliminar imports sin uso.
- Evitar comodines en imports.
- Mantener longitud razonable de línea; dividir llamadas complejas por argumento.
- No conservar espacios finales ni múltiples líneas vacías consecutivas.

## 4.3 Tipos y Nulabilidad

- Preferir tipos no nulos.
- Utilizar `require` para precondiciones del llamador y `check` para estado interno.
- Emplear `?.`, `?:` y transformaciones seguras en lugar de `!!`.
- Utilizar `data class` para valores inmutables sin identidad propia.
- Utilizar `enum class` o clases selladas para conjuntos cerrados.
- Exponer colecciones de solo lectura cuando no se requiera mutación.
- Evitar `Any`, casts no verificados y mapas de cadenas si existe un modelo tipado viable.

## 4.4 Funciones y Clases

- Una función debe realizar una operación coherente y mantener un nivel de abstracción.
- Preferir retornos tempranos para reducir anidamiento.
- Usar inyección por constructor para clientes, relojes y colaboradores reemplazables.
- Mantener dependencias externas detrás de clases o interfaces con propósito definido.
- Crear una abstracción solo cuando elimine duplicación real o represente una variación del dominio.
- Limitar el uso de `companion object` a constantes y fábricas relacionadas con el tipo.

## 4.5 Inmutabilidad y Efectos

- Preferir `val` sobre `var`.
- Separar cálculo puro de operaciones de archivos, red o procesos.
- No modificar colecciones recibidas por parámetro.
- Hacer visibles los efectos en el nombre y contrato de la función.
- Las operaciones destructivas deben requerir confirmación o una opción explícita.

# 5. Manejo de Errores y Recursos

## 5.1 Reglas

- Capturar excepciones en los límites de archivos, red y procesos externos.
- No utilizar `catch (Exception)` salvo en un límite superior que convierta el error en resultado controlado.
- Preservar la causa al crear una excepción con mayor contexto.
- Mostrar mensajes accionables: operación, componente afectado y posible corrección.
- Enviar errores y advertencias a `stderr`; reservar `stdout` para resultados, especialmente en modo JSON.
- Continuar el análisis si falla una fuente opcional y existe información parcial válida.

## 5.2 Excepciones

| Situación | Tratamiento recomendado |
|-----------|-------------------------|
| Argumento inválido | `IllegalArgumentException` o error de validación Clikt |
| Estado interno imposible | `IllegalStateException` o `check` |
| Archivo inexistente | Mensaje operativo con ruta y acción sugerida |
| Error HTTP | Resultado controlado con código, fuente y posibilidad de fallback |
| Timeout de proceso | Cancelación, advertencia y recomendación de ajustar `--timeout` |
| Respuesta incompleta | Omitir el dato inválido y conservar advertencia |

## 5.3 Recursos

- Cerrar respuestas HTTP, streams y procesos mediante `use` o bloques equivalentes.
- Definir timeouts explícitos para red y procesos.
- Evitar cargar archivos arbitrariamente grandes en memoria sin límite.
- Restaurar el estado de terminal al cerrar la TUI, incluso ante excepciones.

# 6. Seguridad

1. No registrar tokens, contraseñas, cabeceras sensibles ni URLs con credenciales.
2. Leer `OSS_INDEX_TOKEN`, `NVD_API_KEY`, `SONAR_TOKEN` y `SNYK_TOKEN` desde variables de entorno o secretos CI.
3. Enviar credenciales de repositorios solo por HTTPS.
4. Aplicar `DEPANALYZER_TRUSTED_CREDENTIAL_HOSTS` como allowlist y denegar por defecto.
5. Validar y normalizar URLs antes de compararlas con hosts confiables.
6. Tratar archivos, respuestas HTTP y salida de procesos como entradas no confiables.
7. No ejecutar comandos construidos mediante concatenación de texto no validado.
8. No modificar archivos de build sin aprobación, `--apply-id` o flujo explícito.
9. Crear backup antes de aplicar una actualización.
10. Evitar incluir datos sensibles en reportes, telemetría y excepciones.

# 7. Pruebas

## 7.1 Convenciones

- Framework: JUnit 5 y assertions de Kotlin.
- Mocking: MockK únicamente cuando una implementación real o fake simple no sea adecuada.
- HTTP: MockWebServer para respuestas, errores, autenticación y timeouts.
- Patrón de archivos: `src/test/kotlin/**/*Test.kt`.
- Patrón de método: nombre descriptivo entre acentos graves, por ejemplo
  ``fun `should preserve dependencies when OSS Index fails`()``.
- Estructura recomendada: Arrange, Act, Assert; los comentarios son opcionales si el código ya expresa las fases.

## 7.2 Cobertura por Cambio

La cobertura de líneas del núcleo medible debe ser mayor o igual al 70% en JaCoCo y Sonar. Se excluyen únicamente
fronteras que dependen directamente de una terminal interactiva o de procesos externos:

- `AnalyzeTuiApp`, cuyo bucle depende de TTY y entrada nativa;
- `BaseAnalyzeCommand`, adaptador de orquestación CLI cubierto por pruebas de interfaz;
- el selector interactivo de `Update`, cubierto por pruebas de flujo y actualización;
- `GradleCommandExecutor`, adaptador de procesos cubierto por pruebas de integración.

Estas exclusiones no eliminan sus pruebas. Solo evitan que detalles dependientes de plataforma distorsionen la métrica
estructural; sus resultados se reportan en las suites de interfaz e integración.

La mutación se concentra en reglas puras y críticas: grafo de dependencias, construcción del árbol, clasificación de
vulnerabilidades y validación de entradas. Los adaptadores de red, terminal y procesos se validan mediante integración y
no se incluyen en PIT para mantener una ejecución reproducible dentro del límite del pipeline.

La línea base de PIT exige mutation score mayor o igual al 45% y cobertura de las clases mutadas mayor o igual al 65%.
Estos umbrales deben incrementarse cuando se añadan pruebas que eliminen mutantes supervivientes.

| Cambio | Pruebas mínimas esperadas |
|--------|---------------------------|
| Nuevo parser | Archivo válido, sintaxis alternativa, campo ausente y entrada inválida |
| Cliente HTTP | Éxito, error, autenticación, timeout y respuesta parcial |
| Opción CLI | Valor predeterminado, opción explícita, combinación inválida y código de salida |
| Formato JSON | Serialización válida, listas vacías y ausencia de ruido en `stdout` |
| Actualizador | Dry-run, cambio seleccionado, archivo no compatible y backup |
| TUI | Estado inicial, navegación, filtros y terminal reducida |
| Corrección de bug | Test de regresión que falla antes de la corrección |

## 7.3 Tipos de Prueba y Evidencia

| Tipo | Ubicación / Evidencia |
|------|------------------------|
| Unitarias | `src/test/kotlin/com/depanalyzer/**` |
| Integración | `src/test/kotlin/com/depanalyzer/integration/**` |
| Interfaz | Pruebas de `cli` y `tui` |
| Mutación | `build/reports/pitest` |
| MCP | `npm test` en `integrations/mcp` |
| Análisis estático | Semgrep y Sonar |
| Dependencias | Snyk |

Las pruebas no deben depender de APIs reales, credenciales personales, orden de ejecución ni conectividad externa salvo que
sean pruebas end-to-end explícitamente separadas.

# 8. Documentación

- Mantener `README.md` como guía operativa principal.
- Mantener FD01-FD05, Diccionario de Datos y Estándar de Programación en `docs/`.
- Utilizar carátula institucional, salto de página, identificación del documento, control de versiones e índice.
- Emplear títulos numerados y una jerarquía consistente.
- Actualizar FD03 cuando cambien requerimientos y FD04 cuando cambien arquitectura o evidencias.
- Actualizar FD05 cuando cambien alcance, resultados o conclusiones.
- Documentar clases y funciones públicas cuando su contrato no sea evidente.
- Generar documentación técnica mediante Dokka y publicarla en `/api-docs/`.
- Evitar afirmar métricas o resultados que no puedan verificarse en el repositorio o CI.

# 9. Git y Revisión de Cambios

## 9.1 Commits

- No ejecutar `git add`, `git commit` ni `git push` sin autorización explícita.
- Utilizar Conventional Commits: `feat:`, `fix:`, `docs:`, `test:`, `refactor:` o `chore:`.
- Mantener cada commit enfocado en una intención.
- No incluir `build`, `node_modules`, archivos temporales, secretos ni carpetas locales no autorizadas.
- No reescribir historial compartido sin aprobación.

## 9.2 Revisión

La revisión debe priorizar:

1. Corrección y regresiones.
2. Seguridad y exposición de credenciales.
3. Compatibilidad del JSON y la CLI.
4. Manejo de errores y recursos.
5. Pruebas faltantes.
6. Consistencia arquitectónica.
7. Legibilidad y documentación.

# 10. Automatización y Criterios de Aceptación

## 10.1 Comandos Principales

```bash
./gradlew test
./gradlew assemble
./gradlew pitest
```

Para MCP:

```bash
cd integrations/mcp
npm ci
npm test
```

## 10.2 Definición de Terminado

Un cambio se considera terminado cuando:

- Compila con el JDK y Gradle definidos por el proyecto.
- Las pruebas relevantes pasan.
- Incluye pruebas nuevas cuando modifica comportamiento.
- No expone secretos ni introduce destinos de credenciales no confiables.
- Mantiene la salida JSON y los códigos de salida compatibles, o documenta el cambio.
- Actualiza los documentos afectados.
- No contiene archivos generados o cambios ajenos al alcance.
- Puede ser entendido y revisado por otro integrante del equipo.
