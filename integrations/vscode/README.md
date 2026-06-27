# DepAnalyzer Security

Analiza dependencias vulnerables y desactualizadas sin salir de Visual Studio Code.

## Funciones

- Vista **DepAnalyzer** en la barra de actividad.
- Resumen visual con grupos por severidad, dependencias desactualizadas y hallazgos sin ubicacion.
- Panel de detalle al hacer clic en vulnerabilidades, con CVE, CVSS, version actual, version sugerida y acciones.
- Accesos rapidos para abrir el archivo afectado, ver la referencia CVE o aplicar una actualizacion cuando el CLI lo permita.
- Análisis manual, automático y al guardar.
- Diagnósticos directamente en archivos Maven, Gradle, npm y Python.
- Información de CVE, severidad, CVSS y enlaces desde el editor.
- Quick Fix para aplicar actualizaciones directas aprobadas.
- Proveedores OSS Index y NVD mediante el CLI de DepAnalyzer.

## Requisito: instalar el CLI

La extensión utiliza el ejecutable `depanalyzer`. Instálalo antes de iniciar el análisis.
La versión 0.1.2 detecta automáticamente las interfaces actuales y anteriores del CLI.

### Windows

Con Scoop:

```powershell
scoop bucket add andre https://github.com/andre-carbajal/scoop-bucket
scoop install andre/depanalyzer
scoop update depanalyzer
```

También puedes descargar `depanalyzer-windows-amd64.zip` desde
[GitHub Releases](https://github.com/UPT-FAING-EPIS/proyecto-si784-2026-i-u2-analizador-de-dependencias-2/releases),
descomprimirlo y configurar:

```json
{
  "depanalyzer.executablePath": "C:\\ruta\\depanalyzer.exe"
}
```

### macOS y Linux

```bash
brew tap andre-carbajal/homebrew-tap
brew install depanalyzer
```

También puedes usar Snap:

```bash
sudo snap install depanalyzer
```

Si el ejecutable está disponible en `PATH`, no necesitas configurar `depanalyzer.executablePath`.

## Primer análisis

1. Abre un proyecto que contenga `pom.xml`, `build.gradle`, `build.gradle.kts`, `package.json`,
   `pyproject.toml` o `requirements.txt`.
2. Selecciona el icono **DepAnalyzer** de la barra lateral.
3. Ejecuta `DepAnalyzer: Analizar Workspace` desde la paleta de comandos.
4. Revisa el resumen por severidad y abre cada hallazgo para ver su detalle.
5. Usa las acciones del panel para saltar al archivo, revisar la CVE o aplicar una actualizacion disponible.

## Configuración

| Propiedad | Valor inicial | Descripción |
|-----------|---------------|-------------|
| `depanalyzer.executablePath` | vacío | Ruta al ejecutable; vacío utiliza `PATH`. |
| `depanalyzer.autoAnalyze` | `true` | Analiza al activar la extensión. |
| `depanalyzer.scanOnSave` | `true` | Reanaliza al guardar manifiestos. |
| `depanalyzer.dynamic` | `false` | Ejecuta análisis dinámico Maven/Gradle. |
| `depanalyzer.provider` | `auto` | Selecciona `auto`, `oss` o `nvd`. |
| `depanalyzer.timeoutSeconds` | `900` | Tiempo máximo del análisis. |

Para análisis completos se recomienda configurar `OSS_INDEX_TOKEN` y, opcionalmente, `NVD_API_KEY` en el entorno donde
se inicia Visual Studio Code.

## Privacidad

La extensión ejecuta el CLI con `--no-telemetry`. Las consultas de vulnerabilidades pueden comunicarse con OSS Index o
NVD según la configuración elegida.

## Problemas y código fuente

- [Reportar un problema](https://github.com/UPT-FAING-EPIS/proyecto-si784-2026-i-u1-analizador-de-dependencias/issues)
- [Código fuente](https://github.com/UPT-FAING-EPIS/proyecto-si784-2026-i-u1-analizador-de-dependencias)
- [Documentación](https://upt-faing-epis.github.io/proyecto-si784-2026-i-u1-analizador-de-dependencias/)
