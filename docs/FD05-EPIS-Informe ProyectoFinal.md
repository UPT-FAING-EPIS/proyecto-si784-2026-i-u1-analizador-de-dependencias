<center>

![Logo UPT](./media/logo-upt.png)

**UNIVERSIDAD PRIVADA DE TACNA**

**FACULTAD DE INGENIERÍA**

**Escuela Profesional de Ingeniería de Sistemas**

**Informe Final**

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

| CONTROL DE VERSIONES |           |              |               |            |                                      |
|:--------------------:|:----------|:-------------|:--------------|:-----------|:-------------------------------------|
|       Versión        | Hecha por | Revisada por | Aprobada por  | Fecha      | Motivo                               |
|         1.0          | ACV, FYG  | ACV, FYG     | P. Cuadros Q. | 2026-06-22 | Versión final del informe de proyecto |

# ÍNDICE GENERAL

1. [Antecedentes](#antecedentes)
2. [Planteamiento del Problema](#planteamiento-del-problema)
    1. [Problema](#problema)
    2. [Justificación](#justificación)
    3. [Alcance](#alcance)
3. [Objetivos](#objetivos)
    1. [Objetivo General](#objetivo-general)
    2. [Objetivos Específicos](#objetivos-específicos)
4. [Marco Teórico](#marco-teórico)
5. [Desarrollo de la Solución](#desarrollo-de-la-solución)
    1. [Análisis de Factibilidad](#análisis-de-factibilidad)
    2. [Tecnología de Desarrollo](#tecnología-de-desarrollo)
    3. [Metodología de Implementación](#metodología-de-implementación)
    4. [Módulos Implementados](#módulos-implementados)
6. [Cronograma](#cronograma)
7. [Presupuesto](#presupuesto)
8. [Evidencias de Calidad](#evidencias-de-calidad)
9. [Conclusiones](#conclusiones)
10. [Recomendaciones](#recomendaciones)
11. [Anexos](#anexos)

<div style="page-break-after: always; visibility: hidden">\pagebreak</div>

# Antecedentes

Los proyectos modernos dependen de bibliotecas de terceros para acelerar el desarrollo, reducir esfuerzo repetitivo y
aprovechar soluciones mantenidas por comunidades técnicas. Sin embargo, esta dependencia introduce riesgos de seguridad,
mantenibilidad y compatibilidad cuando las versiones quedan desactualizadas o cuando componentes transitivos incorporan
vulnerabilidades conocidas.

En entornos académicos y de desarrollo, la revisión manual de dependencias suele ser lenta, incompleta y difícil de
repetir. Los equipos deben revisar archivos Maven, Gradle, npm o Python, consultar fuentes externas de vulnerabilidades,
verificar versiones recientes y consolidar evidencia para reportes de calidad. Esta dispersión dificulta tener una visión
confiable del estado real del proyecto.

Frente a esta problemática, se implementó **DepAnalyzer**, una herramienta CLI/TUI desarrollada en Kotlin que automatiza
el análisis de dependencias multi-ecosistema, detecta versiones desactualizadas, consulta CVEs mediante OSS Index y NVD,
y publica evidencias de calidad en GitHub Pages para facilitar evaluación y trazabilidad.

# Planteamiento del Problema

## Problema

Los equipos de desarrollo necesitan controlar el riesgo asociado a dependencias externas, pero suelen enfrentar tres
limitaciones principales:

1. Los archivos de configuración varían según ecosistema: `pom.xml`, `build.gradle`, `build.gradle.kts`,
   `package.json`, `pyproject.toml` o `requirements.txt`.
2. Las vulnerabilidades pueden encontrarse en dependencias directas o transitivas, por lo que no siempre son visibles en
   el archivo principal del proyecto.
3. La evidencia de pruebas y análisis de calidad se genera en herramientas separadas, lo que dificulta presentarla de
   manera ordenada ante un evaluador.

Como resultado, el diagnóstico de seguridad y actualización de dependencias puede depender de búsquedas manuales,
criterio individual o reportes aislados sin trazabilidad suficiente.

## Justificación

La implementación de DepAnalyzer se justifica por la necesidad de contar con una herramienta local, automatizable y
documentada que permita revisar el estado de dependencias en proyectos de software. El sistema aporta valor académico y
técnico porque:

- Reduce el tiempo de diagnóstico de dependencias vulnerables o desactualizadas.
- Integra consultas a OSS Index y NVD para obtener información de CVEs.
- Produce reportes legibles y salida JSON para integración con CI/CD.
- Incluye una interfaz TUI para exploración interactiva desde terminal.
- Centraliza documentos FD y reportes de calidad en GitHub Pages.

## Alcance

El alcance comprende análisis, diseño, construcción, pruebas, documentación y publicación de evidencias del sistema
DepAnalyzer. La solución incluye:

- Soporte para Maven, Gradle Groovy, Gradle Kotlin, npm, Poetry y `requirements.txt`.
- Comandos CLI `analyze`, `tui` y `update`.
- Consulta de vulnerabilidades con OSS Index, NVD o modo automático.
- Clasificación de vulnerabilidades directas y transitivas.
- Exportación JSON para integración con pipelines.
- Actualización guiada de dependencias con confirmación y backup.
- Servidor MCP para integración con agentes compatibles.
- Publicación de documentación y reportes en GitHub Pages.

No se incluye una interfaz web dedicada, una aplicación móvil, remediación automática sin confirmación ni sustitución
completa de plataformas SCA empresariales.

# Objetivos

## Objetivo General

Implementar una herramienta CLI/TUI multi-ecosistema que permita analizar dependencias de proyectos de software,
identificar versiones desactualizadas, detectar vulnerabilidades CVE y generar evidencia técnica verificable para
procesos de calidad y pruebas.

## Objetivos Específicos

- Desarrollar parsers para archivos Maven, Gradle, npm y Python.
- Implementar consulta de vulnerabilidades mediante OSS Index y NVD.
- Clasificar hallazgos por severidad, dependencia directa y dependencia transitiva.
- Generar reportes por consola y JSON para uso humano y automatizado.
- Implementar actualización guiada con simulación, confirmación y backup.
- Validar el sistema con pruebas unitarias, integración, interfaz, mutación y análisis estático.
- Publicar documentación y reportes de evidencia mediante GitHub Pages.

# Marco Teórico

**Análisis de Composición de Software (SCA).** El SCA permite identificar componentes de terceros, evaluar su estado de
actualización y detectar vulnerabilidades conocidas. Es una práctica clave en seguridad de software moderno.

**CVE y CVSS.** CVE es un identificador estándar para vulnerabilidades conocidas. CVSS permite expresar severidad mediante
puntajes y categorías como `LOW`, `MEDIUM`, `HIGH` y `CRITICAL`.

**Dependencias transitivas.** Una dependencia transitiva es incorporada indirectamente por otra dependencia directa. Su
análisis es importante porque puede introducir riesgos aunque no aparezca declarada explícitamente en el archivo build.

**CLI/TUI.** Una CLI permite automatización mediante comandos y scripts. Una TUI agrega interacción visual en terminal sin
requerir interfaz gráfica web o de escritorio.

**GitHub Actions y GitHub Pages.** GitHub Actions automatiza pruebas, análisis y generación de artefactos. GitHub Pages
permite publicar documentación estática y reportes de evidencia accesibles desde un enlace público.

# Desarrollo de la Solución

## Análisis de Factibilidad

### Factibilidad Técnica

El proyecto es técnicamente factible. Kotlin, Gradle, Clikt, OkHttp, Jackson y JUnit 5 son tecnologías maduras y
adecuadas para una herramienta CLI/TUI. El equipo cuenta con repositorio GitHub, workflows de CI y estructura modular
para evolucionar parsers, reportes y clientes externos.

### Factibilidad Económica

La inversión directa es baja porque el proyecto utiliza herramientas open-source y servicios gratuitos para el contexto
académico. Los costos se concentran en tiempo de desarrollo, conectividad, ejecución de pipelines y configuración de
reportes.

| Componente de Inversión | Tipo | Monto |
|-------------------------|------|-------|
| Costos de personal del equipo | Personal | S/ 5,000.00 |
| Costos generales (conectividad, energía, útiles) | Generales | S/ 250.00 |
| Costos del ambiente (GitHub, CI/CD, publicación) | Infraestructura | S/ 0.00 |
| Tokens/API keys para pruebas controladas | Operación | S/ 0.00 |
| **INVERSIÓN TOTAL ESTIMADA** |  | **S/ 5,250.00** |

### Factibilidad Operativa

La herramienta se ejecuta desde terminal y se integra con workflows de CI/CD, por lo que su adopción no requiere
infraestructura compleja. La publicación de evidencia en GitHub Pages facilita la revisión docente.

### Factibilidad Legal

El sistema usa bibliotecas open-source y APIs públicas respetando sus términos de uso. Los tokens se gestionan mediante
variables de entorno y secretos de GitHub, evitando exposición de credenciales.

### Factibilidad Social y Ambiental

El proyecto fortalece prácticas de seguridad y calidad en estudiantes de ingeniería. Además, reduce uso de documentos
físicos al centralizar evidencia en repositorio y GitHub Pages.

## Tecnología de Desarrollo

| Capa | Tecnología | Propósito |
|------|------------|-----------|
| Lenguaje | Kotlin JVM | Implementación principal de la CLI/TUI. |
| Build | Gradle Kotlin DSL | Compilación, pruebas, empaquetado y plugins de calidad. |
| CLI | Clikt / Mordant | Definición de comandos y salida visual en consola. |
| HTTP | OkHttp | Comunicación con OSS Index, NVD y repositorios. |
| JSON/XML | Jackson / Maven Model | Serialización, parseo XML y reportes JSON. |
| Pruebas | JUnit 5, MockK, MockWebServer | Validación unitaria, integración y clientes HTTP. |
| Calidad | Semgrep, Snyk, Sonar, PIT | Análisis estático, seguridad y pruebas de mutación. |
| Documentación | Markdown, Dokka, GitHub Pages | Documentos FD, API docs y reportes publicados. |
| Integración | GitHub Actions, MCP | CI/CD y automatización compatible con agentes. |

## Metodología de Implementación

Se aplicó una metodología incremental orientada a evidencia, tomando como base los documentos FD01-FD04 y validando cada
módulo mediante pruebas automatizadas.

| Fase | Actividades Principales | Producto Esperado |
|------|-------------------------|-------------------|
| Concepción | Definición del problema, alcance, riesgos y factibilidad. | FD01 y FD02 alineados al proyecto. |
| Elaboración | Requerimientos, arquitectura, módulos y criterios de calidad. | FD03, FD04 y backlog técnico. |
| Construcción | Implementación CLI/TUI, parsers, clientes externos, reportes y update. | Incrementos funcionales probados. |
| Transición | Publicación de documentación, reportes y validación final. | GitHub Pages, FD05 y evidencias de calidad. |

## Módulos Implementados

| Módulo | Descripción | Evidencia |
|--------|-------------|-----------|
| CLI | Comandos `analyze`, `tui`, `update` y flags de ejecución. | `src/main/kotlin/com/depanalyzer/cli` |
| Core | Orquestación de detección, parseo, consulta y reporte. | `src/main/kotlin/com/depanalyzer/core` |
| Parser | Lectura de Maven, Gradle, npm y Python. | `src/main/kotlin/com/depanalyzer/parser` |
| Repository | Clientes OSS Index, NVD y repositorios de versiones. | `src/main/kotlin/com/depanalyzer/repository` |
| Report | Modelos de reporte, JSON y renderizado de consola. | `src/main/kotlin/com/depanalyzer/report` |
| Update | Planificación y aplicación segura de actualizaciones. | `src/main/kotlin/com/depanalyzer/update` |
| TUI | Interfaz interactiva de terminal. | `src/main/kotlin/com/depanalyzer/tui` |
| MCP | Servidor para integración con agentes. | `integrations/mcp` |

# Cronograma

El proyecto se desarrolló durante la Unidad 2 del curso, con cierre documental y publicación de evidencias en junio de
2026.

| Actividad / Fase | Sem. 1-2 | Sem. 3-4 | Sem. 5-6 | Sem. 7-8 | Sem. 9-10 |
|------------------|:--------:|:--------:|:--------:|:--------:|:---------:|
| Levantamiento, alcance y factibilidad | X |  |  |  |  |
| Visión, requerimientos y arquitectura | X | X |  |  |  |
| Implementación de parsers y análisis |  | X | X |  |  |
| Reportes, TUI y actualización guiada |  |  | X | X |  |
| Pruebas, calidad y documentación |  |  |  | X | X |
| Publicación GitHub Pages y cierre FD05 |  |  |  |  | X |

# Presupuesto

## Inversión de Desarrollo

| Componente de Inversión | Monto |
|-------------------------|-------|
| Costos de personal del equipo de desarrollo | S/ 5,000.00 |
| Costos generales (conectividad, energía eléctrica y útiles) | S/ 250.00 |
| Costos del ambiente (GitHub Actions, GitHub Pages, herramientas open-source) | S/ 0.00 |
| **INVERSIÓN TOTAL DE DESARROLLO** | **S/ 5,250.00** |

## Evaluación Financiera

| Indicador | Resultado e Interpretación |
|-----------|----------------------------|
| VAN | Positivo en términos académicos por ahorro de tiempo de revisión y reducción de riesgo técnico. |
| TIR | Favorable al no requerir licencias comerciales obligatorias. |
| B/C | Mayor a 1 al centralizar análisis, reportes y documentación en una solución automatizable. |

# Evidencias de Calidad

Las evidencias se publican desde GitHub Pages:

- [Reportes de pruebas y calidad](./reports/)
- [Documentación autogenerada de la aplicación](./api-docs/)
- [FD04 - Arquitectura con enlaces de evidencia](./FD04-EPIS-Informe%20Arquitectura%20de%20Software.html)

Reportes considerados:

- Sonar
- Semgrep
- Snyk
- Pruebas unitarias
- Pruebas de integración
- Pruebas de mutación
- Pruebas de interfaz
- Pruebas BDD
- Documentación Dokka

# Conclusiones

- El análisis integral confirma que DepAnalyzer es viable técnica y operativamente para el contexto académico del curso.
- La herramienta automatiza la detección de dependencias desactualizadas y vulnerabilidades en múltiples ecosistemas.
- La arquitectura modular permite mantener y ampliar parsers, fuentes de vulnerabilidades, reportes y comandos.
- Los workflows de CI/CD y GitHub Pages centralizan evidencia documental y técnica para evaluación docente.
- La salida JSON, el modo TUI y el comando `update` convierten al sistema en una herramienta utilizable tanto manualmente
  como en automatizaciones.

# Recomendaciones

- Mantener actualizados los tokens `OSS_INDEX_TOKEN`, `NVD_API_KEY`, `SONAR_TOKEN` y `SNYK_TOKEN` como secretos de GitHub.
- Revisar periódicamente los reportes publicados en GitHub Pages después de cada push a `main`.
- Ampliar la cobertura de pruebas BDD con escenarios de usuario final para `analyze`, `tui` y `update`.
- Optimizar la duración de pruebas de mutación para que el reporte PIT complete dentro del tiempo del workflow.
- Mantener sincronizados README, FD03, FD04 y FD05 cuando se agreguen nuevos ecosistemas o comandos.

# Anexos

# Anexo 01 Informe de Factibilidad

Contiene el análisis integral de factibilidad técnica, económica, operativa, legal, social y ambiental del proyecto.

# Anexo 02 Documento de Visión

Define la visión general del sistema, interesados, usuarios, características del producto, restricciones y criterios de
calidad.

# Anexo 03 Documento SRS

Documenta los requerimientos funcionales, no funcionales, reglas de negocio, modelos conceptuales y trazabilidad técnica
del sistema.

# Anexo 04 Documento SAD

Describe la arquitectura bajo el modelo 4+1, las vistas lógicas, de implementación, procesos, despliegue y atributos de
calidad.

# Anexo 05 Manuales y otros documentos

Comprende README, diccionario de datos, estándar de programación, documentación autogenerada y reportes de pruebas
publicados en GitHub Pages.
