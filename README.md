# Analizador de Dependencias Multi-Lenguaje

Herramienta CLI para analizar proyectos Java (Maven/Gradle), JavaScript/TypeScript (npm) y Python
(Poetry/requirements.txt), detectar dependencias desactualizadas y encontrar vulnerabilidades (CVE) con OSS Index
y, opcionalmente, NIST NVD para ecosistema Maven.

## Caracteristicas

- Soporta Maven (`pom.xml`), Gradle Groovy (`build.gradle`) y Gradle Kotlin (`build.gradle.kts`).
- Soporta npm (`package.json` + `package-lock.json`) para proyectos JavaScript/TypeScript.
- Soporta Python con Poetry (`pyproject.toml` + `poetry.lock`) y `requirements.txt`.
- Detecta automaticamente el tipo de proyecto.
- Identifica dependencias desactualizadas y sugiere versiones mas nuevas.
- Detecta vulnerabilidades CVE via OSS Index (Maven, npm y PyPI).
- Enriquece CVEs con NIST NVD (CVSS v3 y metadata oficial) para Maven.
- Incluye modo interactivo (`tui`) y modo de actualizacion asistida (`update`).

## Instalacion

### Opcion recomendada (package managers)

Homebrew (macOS/Linux):

```bash
brew tap andre-carbajal/homebrew-tap
brew install depanalyzer
```

Scoop (Windows PowerShell):

```powershell
scoop bucket add scoop-bucket https://github.com/andre-carbajal/scoop-bucket
scoop install depanalyzer
```

Snap (Linux):

```bash
sudo snap install depanalyzer
```

### Opcion alternativa (script instalador del repo)

Linux/macOS:

```bash
curl -fsSL https://raw.githubusercontent.com/UPT-FAING-EPIS/proyecto-si784-2026-i-u1-analizador-de-dependencias/main/scripts/install.sh | sh
```

Version especifica:

```bash
curl -fsSL https://raw.githubusercontent.com/UPT-FAING-EPIS/proyecto-si784-2026-i-u1-analizador-de-dependencias/main/scripts/install.sh | sh -s -- --version v1.0.0
```

Windows:

- Descarga el `.zip` desde GitHub Releases.
- Descomprime y ejecuta `depanalyzer.exe`.

### Actualizar desde package manager

- Homebrew: `brew upgrade depanalyzer`
- Scoop: `scoop update depanalyzer`
- Snap: `sudo snap refresh depanalyzer`

## Como obtener y configurar OSS Index

El analizador usa OSS Index para consultar vulnerabilidades.

### Limites de API

| Configuracion | Limite aproximado | Recomendado para              |
|:--------------|:------------------|:------------------------------|
| Sin token     | ~120 req/hora     | pruebas puntuales             |
| Con token     | ~1,000+ req/hora  | desarrollo, CI/CD, produccion |

### Obtener token

1. Ve a `https://guide.sonatype.com/`.
2. Inicia sesion.
3. Entra a `Settings` -> `Personal Access Tokens`.
4. Genera un token nuevo.
5. Copialo inmediatamente.

### Configurar token (recomendado: variable de entorno)

Linux/macOS:

```bash
export OSS_INDEX_TOKEN="tu_token"
```

Windows PowerShell:

```powershell
$env:OSS_INDEX_TOKEN="tu_token"
```

Tambien puedes pasarlo por CLI con `--oss-token` (ver tablas de subcomandos).

## Como obtener y configurar NIST NVD

NVD es opcional; puedes forzarlo con `--nvd`.

Seleccion de fuente de vulnerabilidades:

- Modo default (sin `--oss` ni `--nvd`): prioridad OSS Index. Si OSS no responde o falla autenticacion, hace fallback a
  NVD.
- `--oss`: fuerza solo OSS Index (sin fallback).
- `--nvd`: fuerza solo NVD (sin fallback).
- Si usas `--oss` o `--nvd` y falla la fuente (red/401/timeout), el comando reporta error y no cambia de fuente.

Variables de entorno soportadas para vulnerabilidades:

- `OSS_INDEX_TOKEN`: token de OSS Index.
- `NVD_API_KEY`: API key de NVD.
- Prioridad: si tambien pasas `--oss-token` o `--nvd-token`, la opcion CLI tiene prioridad sobre la variable de entorno.

### Limites de API

| Configuracion | Limite aproximado | Recomendado para              |
|:--------------|:------------------|:------------------------------|
| Sin API key   | ~50 req/hora      | pruebas muy puntuales         |
| Con API key   | ~200+ req/hora    | desarrollo, CI/CD, produccion |

### Obtener API key

1. Ve a `https://nvd.nist.gov/developers/request-an-api-key`.
2. Completa el formulario.
3. Copia tu API key.

### Configurar API key

Linux/macOS:

```bash
export NVD_API_KEY="tu_api_key"
```

Windows PowerShell:

```powershell
$env:NVD_API_KEY="tu_api_key"
```

## Seguridad de Credenciales de Repositorios

Para evitar exfiltracion accidental, las credenciales HTTP Basic solo se adjuntan a hosts confiables explicitamente.

- Variable: `DEPANALYZER_TRUSTED_CREDENTIAL_HOSTS`
- Formato: hosts separados por coma
- Soporte:
    - Host exacto: `nexus.example.com`
    - Sufijo con subdominios: `.corp.example.com`

Si no configuras la variable, el comportamiento es fail-closed: no se envian credenciales.

Ejemplo:

```bash
export DEPANALYZER_TRUSTED_CREDENTIAL_HOSTS="nexus.example.com,.corp.example.com"
```

Notas de seguridad:

- Las credenciales solo se envian por `https`.
- Repositorios `http` nunca reciben credenciales.

## Uso de la CLI

Forma general:

```bash
depanalyzer [opciones-globales] <subcomando> [opciones] [path]
```

Subcomandos disponibles: `analyze`, `tui`, `update`.

### Parametros globales

| Parametro        | Que hace                            | Subcomando | Default     | Ejemplo                                |
|:-----------------|:------------------------------------|:-----------|:------------|:---------------------------------------|
| `--no-telemetry` | Desactiva telemetria anonima de uso | todos      | desactivado | `depanalyzer --no-telemetry analyze .` |
| `-h`, `--help`   | Muestra ayuda                       | todos      | -           | `depanalyzer --help`                   |

### Subcomando `analyze`

Analiza un proyecto y genera reporte de dependencias vulnerables/desactualizadas.

| Parametro            | Que hace                                                            | Subcomando | Default           | Ejemplo                                              |
|:---------------------|:--------------------------------------------------------------------|:-----------|:------------------|:-----------------------------------------------------|
| `<path>`             | Ruta del proyecto a analizar                                        | `analyze`  | `.`               | `depanalyzer analyze ./mi-proyecto`                  |
| `--oss-token`        | Token OSS por CLI (prioridad sobre entorno)                         | `analyze`  | `OSS_INDEX_TOKEN` | `depanalyzer analyze . --oss-token "token"`          |
| `--nvd-token`        | API key NVD por CLI (prioridad sobre entorno)                       | `analyze`  | `NVD_API_KEY`     | `depanalyzer analyze . --nvd-token "token"`          |
| `--oss`              | Fuerza solo OSS (sin fallback)                                      | `analyze`  | `false`           | `depanalyzer analyze . --oss`                        |
| `--nvd`              | Fuerza solo NVD (sin fallback)                                      | `analyze`  | `false`           | `depanalyzer analyze . --nvd`                        |
| `-o`, `--output`     | Exporta salida (`json`)                                             | `analyze`  | salida consola    | `depanalyzer analyze . -o json`                      |
| `--output-file PATH` | Ruta del JSON; use `-` para stdout                                  | `analyze`  | `dependency-report.json` | `depanalyzer analyze . -o json --output-file -` |
| `--quiet`            | Suprime progreso y mensajes informativos                            | `analyze`  | `false`           | `depanalyzer analyze . -o json --output-file - --quiet` |
| `--fail-on-critical` | Retorna exit code 1 si hay CVE CRITICAL                             | `analyze`  | `false`           | `depanalyzer analyze . --fail-on-critical`           |
| `--no-color`         | Desactiva colores ANSI                                              | `analyze`  | `false`           | `depanalyzer analyze . --no-color`                   |
| `--tui`              | Abre interfaz TUI desde `analyze`                                   | `analyze`  | `false`           | `depanalyzer analyze . --tui`                        |
| `-v`, `--verbose`    | Muestra detalle extendido de vulnerabilidades                       | `analyze`  | `false`           | `depanalyzer analyze . --verbose`                    |
| `--show-chains`      | Muestra cadenas de dependencia vulnerables                          | `analyze`  | `false`           | `depanalyzer analyze . --show-chains`                |
| `--chain-detail`     | Muestra detalles completos de cadenas (requiere `--show-chains`)    | `analyze`  | `false`           | `depanalyzer analyze . --show-chains --chain-detail` |
| `--offline`          | Evita Maven `dependency:tree` (analisis estatico)                   | `analyze`  | `false`           | `depanalyzer analyze . --offline`                    |
| `--dynamic`          | Fuerza analisis dinamico (mas preciso, mas lento)                   | `analyze`  | `false`           | `depanalyzer analyze . --dynamic`                    |
| `--disable-maven`    | Desactiva ejecucion Maven en dinamico                               | `analyze`  | `false`           | `depanalyzer analyze . --disable-maven`              |
| `--disable-gradle`   | Desactiva ejecucion Gradle en dinamico                              | `analyze`  | `false`           | `depanalyzer analyze . --disable-gradle`             |
| `--ascii`            | Usa caracteres ASCII para el arbol                                  | `analyze`  | Unicode           | `depanalyzer analyze . --ascii`                      |
| `--tree-depth N`     | Limita profundidad de arbol                                         | `analyze`  | sin limite        | `depanalyzer analyze . --tree-depth 2`               |
| `--tree-expand MODE` | Modo de expansion: `collapsed`, `critical`, `high`, `medium`, `all` | `analyze`  | `all`             | `depanalyzer analyze . --tree-expand high`           |
| `--timeout N`        | Timeout en segundos para descarga de dependencias                   | `analyze`  | `1800`            | `depanalyzer analyze . --timeout 900`                |
| `--command-output`   | Muestra salida de comandos Maven/Gradle en dinamico                 | `analyze`  | `false`           | `depanalyzer analyze . --dynamic --command-output`   |
| `-h`, `--help`       | Ayuda del subcomando                                                | `analyze`  | -                 | `depanalyzer analyze --help`                         |

Ejemplos:

```bash
# Analisis rapido del proyecto actual
depanalyzer analyze .

# JSON para CI/CD
depanalyzer analyze . --output json --no-color

# Falla pipeline si hay criticos
depanalyzer analyze . --fail-on-critical

# Forzar fuente NVD
depanalyzer analyze . --nvd
```

### Subcomando `tui`

Abre la interfaz interactiva en pantalla completa.

`tui` comparte las mismas opciones de analisis que `analyze` (excepto que ya entra directamente en modo interactivo).

| Parametro            | Que hace                                    | Subcomando | Default           | Ejemplo                                          |
|:---------------------|:--------------------------------------------|:-----------|:------------------|:-------------------------------------------------|
| `<path>`             | Ruta del proyecto a analizar                | `tui`      | `.`               | `depanalyzer tui ./mi-proyecto`                  |
| `--oss-token`        | Token OSS por CLI                           | `tui`      | `OSS_INDEX_TOKEN` | `depanalyzer tui . --oss-token "token"`          |
| `--nvd-token`        | API key NVD por CLI                         | `tui`      | `NVD_API_KEY`     | `depanalyzer tui . --nvd-token "token"`          |
| `--oss`              | Fuerza solo OSS (sin fallback)              | `tui`      | `false`           | `depanalyzer tui . --oss`                        |
| `--nvd`              | Fuerza solo NVD (sin fallback)              | `tui`      | `false`           | `depanalyzer tui . --nvd`                        |
| `--no-color`         | Desactiva color (si el entorno lo requiere) | `tui`      | `false`           | `depanalyzer tui . --no-color`                   |
| `-v`, `--verbose`    | Modo detallado                              | `tui`      | `false`           | `depanalyzer tui . --verbose`                    |
| `--show-chains`      | Muestra cadenas de dependencia vulnerables  | `tui`      | `false`           | `depanalyzer tui . --show-chains`                |
| `--chain-detail`     | Detalle completo de cadenas                 | `tui`      | `false`           | `depanalyzer tui . --show-chains --chain-detail` |
| `--offline`          | Analisis estatico                           | `tui`      | `false`           | `depanalyzer tui . --offline`                    |
| `--dynamic`          | Fuerza analisis dinamico                    | `tui`      | `false`           | `depanalyzer tui . --dynamic`                    |
| `--disable-maven`    | Desactiva Maven dinamico                    | `tui`      | `false`           | `depanalyzer tui . --disable-maven`              |
| `--disable-gradle`   | Desactiva Gradle dinamico                   | `tui`      | `false`           | `depanalyzer tui . --disable-gradle`             |
| `--ascii`            | Arbol en ASCII                              | `tui`      | Unicode           | `depanalyzer tui . --ascii`                      |
| `--tree-depth N`     | Limita profundidad de arbol                 | `tui`      | sin limite        | `depanalyzer tui . --tree-depth 3`               |
| `--tree-expand MODE` | Modo de expansion del arbol                 | `tui`      | `all`             | `depanalyzer tui . --tree-expand critical`       |
| `--timeout N`        | Timeout en segundos                         | `tui`      | `1800`            | `depanalyzer tui . --timeout 1200`               |
| `--command-output`   | Salida detallada Maven/Gradle               | `tui`      | `false`           | `depanalyzer tui . --command-output`             |
| `--fail-on-critical` | Exit code 1 con CVE CRITICAL                | `tui`      | `false`           | `depanalyzer tui . --fail-on-critical`           |
| `-h`, `--help`       | Ayuda del subcomando                        | `tui`      | -                 | `depanalyzer tui --help`                         |

Ejemplos:

```bash
# Abrir TUI
depanalyzer tui .

# TUI forzando NVD
depanalyzer tui . --nvd
```

### Subcomando `update`

Propone y aplica actualizaciones en el archivo de build con confirmacion interactiva.

| Parametro         | Que hace                                  | Subcomando | Default           | Ejemplo                                    |
|:------------------|:------------------------------------------|:-----------|:------------------|:-------------------------------------------|
| `<path>`          | Ruta del proyecto a actualizar            | `update`   | `.`               | `depanalyzer update ./mi-proyecto`         |
| `--oss-token`     | Token OSS por CLI                         | `update`   | `OSS_INDEX_TOKEN` | `depanalyzer update . --oss-token "token"` |
| `--dynamic`       | Usa analisis dinamico para plan de update | `update`   | `false`           | `depanalyzer update . --dynamic`           |
| `--dry-run`       | Simula cambios sin escribir archivos      | `update`   | `false`           | `depanalyzer update . --dry-run`           |
| `--only-security` | Solo sugiere updates que corrigen CVEs    | `update`   | `false`           | `depanalyzer update . --only-security`     |
| `--plan`          | Exporta sugerencias JSON sin modificar    | `update`   | `false`           | `depanalyzer update . --plan --output-file -` |
| `--apply-id ID`   | Aplica una sugerencia concreta del plan   | `update`   | -                 | `depanalyzer update . --apply-id abc123`   |
| `--output-file`   | Ruta del plan JSON; use `-` para stdout   | `update`   | `dependency-update-plan.json` | `depanalyzer update . --plan --output-file plan.json` |
| `-h`, `--help`    | Ayuda del subcomando                      | `update`   | -                 | `depanalyzer update --help`                |

Ejemplos:

```bash
# Ver que cambiaria sin tocar archivos
depanalyzer update . --dry-run

# Solo actualizaciones de seguridad
depanalyzer update . --only-security
```

## Recetas rapidas

```bash
# Analizar proyecto actual
depanalyzer analyze .

# Analizar forzando NVD y detalle
depanalyzer analyze . --nvd --verbose

# Generar JSON para pipeline
depanalyzer analyze . --output json --no-color

# Pipeline: fallar por CVEs criticos
depanalyzer analyze . --fail-on-critical

# Simular actualizaciones de dependencias
depanalyzer update . --dry-run
```

## Errores comunes

- `--nvd` sin `NVD_API_KEY`: funciona, pero con limite muy bajo de solicitudes.
- Sin `OSS_INDEX_TOKEN`: el analisis funciona, pero con cupo mas limitado.
- `tui` en CI o sin TTY: puede hacer fallback a salida de consola tradicional.

## Desarrollo

Si vas a contribuir o trabajar en el proyecto internamente, revisa `DEVELOP.md`.
