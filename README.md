# Analizador de Dependencias Java

Herramienta de código abierto desarrollada en Kotlin que analiza proyectos Java (Maven/Gradle), detecta dependencias
desactualizadas y vulnerabilidades de seguridad (CVEs).

## Características

- Soporte para Maven (`pom.xml`), Gradle Groovy (`build.gradle`) y Gradle Kotlin (`build.gradle.kts`).
- Detección automática del tipo de proyecto.
- Resolución de repositorios del proyecto.
- Análisis de CVEs (Sonatype OSS Index).
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

| Opción                    | Descripción                                                                                                      |
|:--------------------------|:-----------------------------------------------------------------------------------------------------------------|
| `<path>`                  | **(Requerido)** Ruta al directorio raíz del proyecto a analizar (ej: `.`).                                       |
| `-t`, `--oss-index-token` | Token de autenticación para OSS Index API. Si no se proporciona, busca la variable de entorno `OSS_INDEX_TOKEN`. |
| `-o`, `--output json`     | Cambia el formato de salida a JSON (útil para automatización).                                                   |
| `--no-color`              | Desactiva los colores ANSI y estilos en la consola (útil para CI/CD).                                            |
| `-v`, `--verbose`         | Modo detallado - muestra la estructura completa del modelo con tabla detallada de vulnerabilidades.              |
| `-h`, `--help`            | Muestra la ayuda del comando y las opciones disponibles.                                                         |

### Ejemplos

```bash
# Analizar el proyecto actual con colores
./build/install/depanalyzer/bin/depanalyzer analyze .

# Generar un reporte en formato JSON
./build/install/depanalyzer/bin/depanalyzer analyze . --output json

# Modo verbose - mostrar tabla detallada de vulnerabilidades
./build/install/depanalyzer/bin/depanalyzer analyze . --verbose

# Modo verbose con JSON (estructura completa del modelo)
./build/install/depanalyzer/bin/depanalyzer analyze . --verbose --output json

# Usar token por línea de comandos (versión larga)
./build/install/depanalyzer/bin/depanalyzer --oss-index-token "tu_token" analyze .

# Usar token por línea de comandos (versión corta)
./build/install/depanalyzer/bin/depanalyzer -t "tu_token" analyze .

# Combinar token + verbose (versión corta)
./build/install/depanalyzer/bin/depanalyzer -t "tu_token" -v analyze .

# Combinar token + verbose + JSON (versión corta)
./build/install/depanalyzer/bin/depanalyzer -t "tu_token" -v -o json analyze .

# Analizar un proyecto en otra ruta sin colores
./build/install/depanalyzer/bin/depanalyzer analyze C:/MisProyectos/JavaApp --no-color
```

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
