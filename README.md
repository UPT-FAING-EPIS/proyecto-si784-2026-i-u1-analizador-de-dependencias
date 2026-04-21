# Analizador de Dependencias Java

Herramienta de código abierto desarrollada en Kotlin que analiza proyectos Java (Maven/Gradle), detecta dependencias
desactualizadas y vulnerabilidades de seguridad (CVEs).

## Características

- Soporte para Maven (`pom.xml`), Gradle Groovy (`build.gradle`) y Gradle Kotlin (`build.gradle.kts`).
- Detección automática del tipo de proyecto.
- Resolución de repositorios del proyecto.
- Análisis de CVEs (Sonatype OSS Index).
- **Enriquecimiento con NIST NVD** - CVSS v3 scores y datos oficiales de CVE.
- Sugerencias de actualización interactivas.

## Requisitos

- JDK 25 o superior.

## Configuración del OSS_INDEX_TOKEN

Para ejecutar análisis completos de vulnerabilidades CVE, necesitas configurar una clave de API de Sonatype OSS Index.

### ¿Por qué es necesario?

El analizador usa [Sonatype OSS Index](https://ossindex.sonatype.org/) para detectar vulnerabilidades.

**Límites de API:**

| Configuración | Límite de Solicitudes | Recomendado para                          |
|:--------------|:----------------------|:------------------------------------------|
| Sin token     | ~120 req/hora         | Pruebas básicas puntuales                 |
| Con token     | ~1,000+ req/hora      | Desarrollo, CI/CD, análisis en producción |

### Obtener un OSS_INDEX_TOKEN

1. Dirígete a [https://guide.sonatype.com/](https://guide.sonatype.com/)
2. **Sign in** con tu cuenta (o crea una nueva si no la tienes)
3. Abre **Settings** desde el menú de usuario
4. Navega a **Personal Access Tokens**
5. Haz clic en **Generate New Token**
    - Dale un nombre descriptivo (ej: "Dependency-Analyzer", "CI/CD Pipeline")
    - Haz clic en **Create**
6. **Copia el token inmediatamente** — no se mostrará de nuevo

### Configurar el token

El token de OSS Index se puede proporcionar de dos formas:

#### Opción 1: Variable de entorno (Recomendado)

**Linux/macOS:**

```bash
export OSS_INDEX_TOKEN="tu_token_aqui"
./build/install/depanalyzer/bin/depanalyzer analyze .
```

**Windows (PowerShell):**

```powershell
$env:OSS_INDEX_TOKEN="tu_token_aqui"
./build/install/depanalyzer/bin/depanalyzer.bat analyze .
```

**Windows (CMD):**

```cmd
set OSS_INDEX_TOKEN=tu_token_aqui
./build/install/depanalyzer/bin/depanalyzer.bat analyze .
```

#### Opción 2: Parámetro de línea de comandos

Puedes pasar el token directamente como parámetro CLI usando `--oss-index-token`:

**Linux/macOS:**

```bash
./build/install/depanalyzer/bin/depanalyzer --oss-index-token "tu_token_aqui" analyze .
```

**Windows:**

```powershell
./build/install/depanalyzer/bin/depanalyzer.bat --oss-index-token "tu_token_aqui" analyze .
```

**Precedencia:** Si se proporciona ambos (variable de entorno y parámetro CLI), el parámetro CLI tiene prioridad.

#### Opción 3: Archivo `.env` (Para desarrollo local)

Crea un archivo `.env` en la raíz del proyecto:

```ini
OSS_INDEX_TOKEN=tu_token_aqui
```

Luego ejecuta:

**Linux/macOS:**

```bash
source .env
./gradlew run
```

**Windows (PowerShell):**

```powershell
Get-Content .env | ForEach-Object { 
    $parts = $_ -split '='; 
    if ($parts.Count -eq 2) { 
        [System.Environment]::SetEnvironmentVariable($parts[0], $parts[1]) 
    } 
}
./gradlew run
```

## Seguridad de Credenciales de Repositorios

Para evitar exfiltración accidental de credenciales al analizar proyectos no confiables, el analizador solo adjunta
credenciales HTTP Basic a hosts explícitamente confiables.

- Variable de entorno: `DEPANALYZER_TRUSTED_CREDENTIAL_HOSTS`
- Formato: hosts separados por coma
- Soporta:
    - Host exacto: `nexus.example.com`
    - Sufijo con subdominios: `.corp.example.com` (incluye `repo.corp.example.com` y `corp.example.com`)

Si la variable no está configurada, el comportamiento por defecto es **fail-closed**: no se envían credenciales a ningún
repositorio.

Ejemplos:

```bash
# Linux/macOS
export DEPANALYZER_TRUSTED_CREDENTIAL_HOSTS="nexus.example.com,.corp.example.com"

# Windows PowerShell
$env:DEPANALYZER_TRUSTED_CREDENTIAL_HOSTS="nexus.example.com,.corp.example.com"
```

Notas de seguridad:

- Las credenciales solo se envían por `https`.
- Repositorios en `http` nunca reciben credenciales.

## Configuración de NIST NVD (Opcional)

Para enriquecer los análisis de vulnerabilidades de OSS Index con datos oficiales de NIST NVD y puntuaciones CVSS v3:

### ¿Por qué usar NVD?

NIST NVD proporciona:

- **CVSS v3.1 scores** - Puntuaciones más actuales y precisas
- **Datos oficiales de CVE** - Información autorizada directamente de NIST
- **Referencias completas** - Enlaces y detalles técnicos adicionales

**Límites de API:**

| Configuración | Límite de Solicitudes | Recomendado para                  |
|:--------------|:----------------------|:----------------------------------|
| Sin token     | ~50 req/hora          | Análisis puntuales (muy limitado) |
| Con token     | ~200+ req/hora        | Desarrollo, CI/CD, producción     |

### Obtener un NVD_API_KEY

1. Dirígete a [https://nvd.nist.gov/developers/request-an-api-key](https://nvd.nist.gov/developers/request-an-api-key)
2. Completa el formulario con:
    - Nombre y email
    - Organización
    - Uso previsto
3. **Copia tu API key inmediatamente** — se mostrará solo una vez
4. Configura la variable de entorno (ver abajo)

### Configurar NVD_API_KEY

#### Opción 1: Variable de entorno (Recomendado)

**Linux/macOS:**

```bash
export NVD_API_KEY="tu_api_key_aqui"
./build/install/depanalyzer/bin/depanalyzer analyze . --use-nvd
```

**Windows (PowerShell):**

```powershell
$env:NVD_API_KEY="tu_api_key_aqui"
./build/install/depanalyzer/bin/depanalyzer.bat analyze . --use-nvd
```

**Windows (CMD):**

```cmd
set NVD_API_KEY=tu_api_key_aqui
./build/install/depanalyzer/bin/depanalyzer.bat analyze . --use-nvd
```

#### Opción 2: Archivo `.env` (Para desarrollo local)

Agrega a tu archivo `.env`:

```ini
OSS_INDEX_TOKEN=tu_token_aqui
NVD_API_KEY=tu_api_key_aqui
```

Luego ejecuta:

```bash
# Linux/macOS
source .env
./gradlew run --args="analyze . --use-nvd"

# Windows (PowerShell)
Get-Content .env | ForEach-Object { 
    $parts = $_ -split '='; 
    if ($parts.Count -eq 2) { 
        [System.Environment]::SetEnvironmentVariable($parts[0], $parts[1]) 
    } 
}
./gradlew run --args="analyze . --use-nvd"
```

### Uso con --use-nvd

El flag `--use-nvd` activa el enriquecimiento con NIST NVD:

```bash
# Con NVD (requiere NVD_API_KEY en el entorno)
./build/install/depanalyzer/bin/depanalyzer analyze . --use-nvd

# Modo verbose con NVD (muestra fuentes de vulnerabilidades)
./build/install/depanalyzer/bin/depanalyzer analyze . --verbose --use-nvd

# Con JSON y NVD
./build/install/depanalyzer/bin/depanalyzer analyze . -o json --use-nvd
```

**Nota:** Si usas `--use-nvd` sin configurar NVD_API_KEY, el analizador funcionará con limitación de ~50 req/hora.

### Estrategia de Enriquecimiento

Cuando `--use-nvd` está activo, el analizador:

1. Obtiene vulnerabilidades de **OSS Index** (como siempre)
2. Busca en **NIST NVD** vulnerabilidades adicionales usando transformación Maven→CPE
3. **Fusiona** los resultados:
    - Misma CVE en ambas fuentes → usa **CVSS v3 de NVD** (más oficial)
    - CVEs solo en NVD → las **incluye automáticamente**
    - CVEs solo en OSS Index → las **mantiene como están**
4. Marca en los resultados la **fuente de cada CVE** (OSS_INDEX, NVD, o BOTH)

**Ejemplo en modo verbose:**

```
🔴 [CVE-2021-12345] CRITICAL (9.8) [Source: BOTH]
   OSS Index Score: 7.5
   NVD CVSS v3: 9.8 ← Usado (más alto)

🔴 [CVE-2022-99999] CRITICAL (9.5) [Source: NVD]
   Encontrado solo en NIST NVD
```

## Instalación para Pruebas

Para preparar el entorno de ejecución local y generar los scripts de inicio:

```bash
./gradlew installDist
```

## Uso de la CLI

Una vez instalada la distribución, puedes ejecutar la herramienta usando el script generado en
`build/install/depanalyzer/bin/`.

### Comando de Análisis

Analiza un proyecto en el directorio especificado:

**Windows (PowerShell/CMD):**

```powershell
./build/install/depanalyzer/bin/depanalyzer.bat analyze <ruta-del-proyecto>
```

**Linux/macOS:**

```bash
./build/install/depanalyzer/bin/depanalyzer analyze <ruta-del-proyecto>
```

### Parámetros y Opciones

El comando `analyze` acepta las siguientes opciones:

| Opción                    | Descripción                                                                                                                                      |
|:--------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------|
| `<path>`                  | **(Opcional)** Ruta al directorio raíz del proyecto a analizar (default: directorio actual `.`).                                                 |
| `-t`, `--oss-index-token` | Token de autenticación para OSS Index API. Si no se proporciona, busca la variable de entorno `OSS_INDEX_TOKEN`.                                 |
| `--use-nvd`               | Enriquece vulnerabilidades con datos de NIST NVD (requiere `NVD_API_KEY`). Ver [Configuración de NIST NVD](#configuración-de-nist-nvd-opcional). |
| `-o`, `--output json`     | Exporta el reporte en JSON al archivo `dependency-report.json` en el directorio actual.                                                          |
| `--fail-on-critical`      | Retorna exit code `1` si se detectan vulnerabilidades `CRITICAL` (útil para CI/CD).                                                              |
| `--no-color`              | Desactiva los colores ANSI y estilos en la consola (útil para CI/CD).                                                                            |
| `--tui`                   | Activa la interfaz interactiva TUI en pantalla completa (buffer alterno). Si no hay TTY o estás en CI/CD, hace fallback automático a CLI plano.  |
| `-v`, `--verbose`         | Modo detallado - muestra la estructura completa del modelo con tabla detallada de vulnerabilidades.                                              |
| `-h`, `--help`            | Muestra la ayuda del comando y las opciones disponibles.                                                                                         |

### Ejemplos

```bash
# Analizar el proyecto actual con colores
./build/install/depanalyzer/bin/depanalyzer analyze .

# Generar un reporte en formato JSON
./build/install/depanalyzer/bin/depanalyzer analyze . --output json

# Analizar usando el directorio actual (sin pasar <path>)
./build/install/depanalyzer/bin/depanalyzer analyze --output json

# Fallar con exit code 1 si hay CVEs críticos (CI/CD)
./build/install/depanalyzer/bin/depanalyzer analyze . --fail-on-critical

# Modo verbose - mostrar tabla detallada de vulnerabilidades
./build/install/depanalyzer/bin/depanalyzer analyze . --verbose

# Modo verbose con JSON (estructura completa del modelo)
./build/install/depanalyzer/bin/depanalyzer analyze . --verbose --output json

# Enriquecer con NIST NVD (requiere NVD_API_KEY configurado)
./build/install/depanalyzer/bin/depanalyzer analyze . --use-nvd

# Modo verbose con NVD (muestra fuentes de CVEs: OSS_INDEX, NVD, BOTH)
./build/install/depanalyzer/bin/depanalyzer analyze . --verbose --use-nvd

# JSON con enriquecimiento NVD
./build/install/depanalyzer/bin/depanalyzer analyze . -o json --use-nvd

# Usar token por línea de comandos (versión larga)
./build/install/depanalyzer/bin/depanalyzer --oss-index-token "tu_token" analyze .

# Usar token por línea de comandos (versión corta)
./build/install/depanalyzer/bin/depanalyzer -t "tu_token" analyze .

# Combinar token + verbose (versión corta)
./build/install/depanalyzer/bin/depanalyzer -t "tu_token" -v analyze .

# Combinar token + verbose + JSON (versión corta)
./build/install/depanalyzer/bin/depanalyzer -t "tu_token" -v -o json analyze .

# Token + NVD + verbose (análisis completo)
./build/install/depanalyzer/bin/depanalyzer -t "tu_token" --use-nvd -v analyze .

# Analizar un proyecto en otra ruta sin colores
./build/install/depanalyzer/bin/depanalyzer analyze C:/MisProyectos/JavaApp --no-color

# Abrir interfaz interactiva desde analyze
./build/install/depanalyzer/bin/depanalyzer analyze . --tui

# Abrir interfaz interactiva con alias dedicado
./build/install/depanalyzer/bin/depanalyzer tui .
```

Notas de TUI:

- En modo TUI el análisis se ejecuta **asíncronamente**: la interfaz abre de inmediato y muestra progreso mientras
  escanea.
- La TUI hace una **precarga rápida de dependencias directas** antes del escaneo dinámico para mostrar resultados desde
  el arranque.
- Para asegurar cobertura de dependencias transitivas, la TUI **fuerza análisis dinámico** (ignora `--offline`,
  `--disable-maven`, `--disable-gradle`).
- Atajos de actualización en TUI: `u` (agregar seleccionada a pendientes), `U` (agregar todas), `a` (aplicar
  pendientes al build file), `x` (descartar pendientes), con confirmación `[s/n]`.
- Si no hay TTY o se está en CI/CD, `--tui` hace fallback automático a salida CLI tradicional.

### Modo Verbose

El parámetro `--verbose` (o `-v`) muestra información detallada del modelo interno de vulnerabilidades:

- **Tabla detallada en consola:** Presenta todas las vulnerabilidades en una tabla con columnas para CVE ID, Severity,
  CVSS Score, Source, Retrieved At, y Affected Dependency.
- **JSON con estructura completa:** Cuando se usa con `-o json`, incluye todos los campos internos del modelo (source,
  timestamp de recuperación, etc.).

Ejemplos:

```bash
# Ver vulnerabilidades en tabla detallada (colores preservados)
./build/install/depanalyzer/bin/depanalyzer analyze . --verbose

# Exportar estructura completa como JSON
./build/install/depanalyzer/bin/depanalyzer analyze . --verbose --output json

# Modo verbose sin colores (útil para piping o CI/CD)
./build/install/depanalyzer/bin/depanalyzer analyze . --verbose --no-color
```

## Desarrollo y Tests

Si deseas ejecutar los tests unitarios:

```bash
./gradlew test
```

## Visualización del Árbol de Dependencias

Por defecto, el analizador muestra las dependencias vulnerables y con actualizaciones disponibles en **formato de árbol
** (similar al comando `tree` de Linux). Esto permite visualizar claramente:

- **Dependencias directas vs transitivas:** Marcadas visualmente (🔴 directo, 🟡 transitivo)
- **Actualizaciones disponibles:** Mostradas con ⬆️ en cada nodo
- **Vulnerabilidades:** Ordenadas por severidad (CRITICAL, HIGH, MEDIUM, LOW)
- **Cadenas de dependencias:** Ver completa la ruta desde una dependencia directa hasta la vulnerable

### Opciones del Árbol

| Opción               | Descripción                                                                                     |
|:---------------------|:------------------------------------------------------------------------------------------------|
| `--ascii`            | Usa caracteres ASCII puro en lugar de Unicode para el árbol (sin emojis).                       |
| `--tree-depth N`     | Limita la profundidad del árbol a N niveles (N es un número entero positivo).                   |
| `--tree-expand MODE` | Modo de expansión del árbol: `collapsed`, `critical`, `high`, `medium`, `all` (default: `all`). |

### Modos de Expansión (`--tree-expand`)

| Modo        | Descripción                                                         |
|:------------|:--------------------------------------------------------------------|
| `collapsed` | Solo muestra dependencias directas con problemas (sin transitivas). |
| `critical`  | Muestra solo ramas con vulnerabilidades CRITICAL.                   |
| `high`      | Muestra ramas con severidad HIGH o superior.                        |
| `medium`    | Muestra ramas con severidad MEDIUM o superior.                      |
| `all`       | Modo por defecto - muestra todas las ramas con problemas.           |

### Ejemplos de Uso del Árbol

```bash
# Ver árbol de dependencias con formato por defecto (Unicode)
./build/install/depanalyzer/bin/depanalyzer analyze .

# Ver árbol en modo ASCII puro (sin emojis)
./build/install/depanalyzer/bin/depanalyzer analyze . --ascii

# Limitar profundidad a 2 niveles
./build/install/depanalyzer/bin/depanalyzer analyze . --tree-depth 2

# Solo mostrar dependencias directas con problemas
./build/install/depanalyzer/bin/depanalyzer analyze . --tree-expand collapsed

# Solo vulnerabilidades CRITICAL
./build/install/depanalyzer/bin/depanalyzer analyze . --tree-expand critical

# Modo verbose con árbol detallado (incluye scope, rutas, descripciones)
./build/install/depanalyzer/bin/depanalyzer analyze . --verbose

# Modo verbose con límite de profundidad
./build/install/depanalyzer/bin/depanalyzer analyze . --verbose --tree-depth 3

# Árbol en modo ASCII + verbose + sin colores (para CI/CD)
./build/install/depanalyzer/bin/depanalyzer analyze . --ascii --verbose --no-color

# Exportar árbol como JSON (preserva estructura jerárquica)
./build/install/depanalyzer/bin/depanalyzer analyze . -o json
```

### Ejemplo de Salida

**Modo Normal (Unicode):**

```
📦 DEPENDENCIAS CON PROBLEMAS
├── 🔴 org.springframework.boot:spring-boot:2.5.0 [DIRECTO]
│   ├── ⬆️ Disponible: 2.7.14
│   └── 🟡 org.yaml:snakeyaml:1.32 [TRANSITIVO]
│       ├── ⬆️ Disponible: 1.33
│       ├── 🔴 [CVE-2021-12345] CRITICAL (9.8)
│       └── 🟠 [CVE-2022-12345] HIGH (7.5)
│
└── 🟠 com.fasterxml.jackson:jackson-databind:2.13.0 [DIRECTO]
    └── ⬆️ Disponible: 2.15.2
```

**Modo ASCII:**

```
[DEPENDENCIAS CON PROBLEMAS]
|
+-- [CRITICAL] org.springframework.boot:spring-boot:2.5.0 [DIRECTO]
|   |-- [UPDATE] Disponible: 2.7.14
|   +-- [HIGH] org.yaml:snakeyaml:1.32 [TRANSITIVO]
|       |-- [UPDATE] Disponible: 1.33
|       +-- [CRITICAL] CVE-2021-12345 CVSS 9.8
|       +-- [HIGH] CVE-2022-12345 CVSS 7.5
```

## CI/CD

El proyecto cuenta con un pipeline de GitHub Actions que ejecuta los tests en cada push a `main` y pull requests.
