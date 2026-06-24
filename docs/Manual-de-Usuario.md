# Manual de Usuario de DepAnalyzer

## 1. Propósito

DepAnalyzer permite revisar dependencias desactualizadas y vulnerabilidades conocidas en proyectos Maven, Gradle, npm
y Python. Este manual describe la instalación, el análisis, la exportación de evidencia y la actualización asistida.

## 2. Instalación

La forma más directa para desarrollo es generar la distribución local:

```bash
./gradlew installDist
```

En Windows, el ejecutable queda en `build/install/depanalyzer/bin/depanalyzer.bat`. En Linux y macOS se utiliza
`build/install/depanalyzer/bin/depanalyzer`.

También se pueden descargar ejecutables desde GitHub Releases. Cada tag `v*` activa la generación para Windows, Linux
y macOS.

## 3. Configuración de seguridad

Para análisis completos se recomienda configurar los secretos como variables de entorno:

```powershell
$env:OSS_INDEX_TOKEN="token"
$env:NVD_API_KEY="api-key"
```

Los tokens nunca deben escribirse en el repositorio ni incluirse en capturas o videos.

## 4. Analizar un proyecto

```bash
depanalyzer analyze C:\ruta\al\proyecto
```

El resultado muestra el tipo de proyecto, dependencias desactualizadas, vulnerabilidades directas, vulnerabilidades
transitivas y recomendaciones.

Para producir JSON:

```bash
depanalyzer analyze . --output json --output-file dependency-report.json --quiet
```

Para usarlo como control de CI:

```bash
depanalyzer analyze . --fail-on-critical --no-color
```

## 5. Interfaz TUI

```bash
depanalyzer tui .
```

Atajos principales:

| Tecla | Acción |
|-------|--------|
| Flechas | Navegar entre dependencias |
| `f` | Cambiar filtro |
| `u` | Preparar actualización seleccionada |
| `U` | Preparar todas las actualizaciones |
| `a` | Aplicar cambios confirmados |
| `x` | Descartar cambios pendientes |
| `q` | Salir |

En una terminal sin TTY, como algunos runners de CI, DepAnalyzer evita abrir la interfaz interactiva y conserva una
salida compatible con automatización.

## 6. Actualización asistida

Para revisar un plan sin modificar archivos:

```bash
depanalyzer update . --dry-run
```

Para exportar un plan estructurado:

```bash
depanalyzer update . --plan --output-file dependency-update-plan.json
```

La aplicación solicita confirmación antes de escribir y crea una copia `.bak` del manifiesto.

## 7. Interpretación del reporte

| Estado | Significado |
|--------|-------------|
| Actualizada | No se encontró una versión posterior |
| Desactualizada | Existe una versión más reciente |
| Vulnerable directa | La dependencia declarada contiene un CVE |
| Vulnerable transitiva | Una dependencia indirecta contiene un CVE |
| Crítica | CVSS mayor o igual que 9.0 |

La ausencia de hallazgos no garantiza ausencia absoluta de vulnerabilidades. El resultado depende de la disponibilidad y
actualización de OSS Index, NVD y los repositorios de paquetes.

## 8. Evidencias de interfaz

El workflow de calidad construye el portal documental, ejecuta Playwright en Chromium y conserva:

- reporte HTML de navegación;
- archivo JUnit para trazabilidad;
- capturas ante fallos;
- trazas de diagnóstico;
- videos WebM de cada escenario.

Estas evidencias se publican en GitHub Pages bajo `reports/interface/` y se conservan como artefactos de GitHub Actions.

## 9. Solución de problemas

- **No reconoce el proyecto:** comprobar que la ruta contenga `pom.xml`, `build.gradle`, `build.gradle.kts`,
  `package.json`, `pyproject.toml`, `poetry.lock` o `requirements.txt`.
- **OSS Index responde 401:** renovar `OSS_INDEX_TOKEN`.
- **NVD tarda demasiado:** configurar `NVD_API_KEY` o usar el modo OSS.
- **La TUI no abre:** ejecutar desde una terminal interactiva real.
- **Una actualización no es compatible:** restaurar el archivo `.bak` y revisar el cambio mayor de versión.
