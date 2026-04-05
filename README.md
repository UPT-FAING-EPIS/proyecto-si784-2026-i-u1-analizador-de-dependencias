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

#### Opción 2: Archivo `.env` (Para desarrollo local)

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

#### Opción 3: CI/CD (GitHub Actions)

Configura el token como un secret en tu repositorio:

1. Dirígete a **Settings** → **Secrets and variables** → **Actions**
2. Haz clic en **New repository secret**
3. Nombre: `OSS_INDEX_TOKEN`
4. Valor: Pega tu token
5. El workflow automáticamente leerá la variable

### Notas de seguridad

⚠️ **IMPORTANTE:**

- Nunca commitees tu token a Git
- No compartas tu token públicamente
- Los tokens en GitHub Actions están protegidos como secrets

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

| Opción                | Descripción                                                                |
|:----------------------|:---------------------------------------------------------------------------|
| `<path>`              | **(Requerido)** Ruta al directorio raíz del proyecto a analizar (ej: `.`). |
| `-o`, `--output json` | Cambia el formato de salida a JSON (útil para automatización).             |
| `--no-color`          | Desactiva los colores ANSI y estilos en la consola (útil para CI/CD).      |
| `-h`, `--help`        | Muestra la ayuda del comando y las opciones disponibles.                   |

### Ejemplos

```bash
# Analizar el proyecto actual con colores
./build/install/depanalyzer/bin/depanalyzer.bat analyze .

# Generar un reporte en formato JSON
./build/install/depanalyzer/bin/depanalyzer.bat analyze . --output json

# Analizar un proyecto en otra ruta sin colores
./build/install/depanalyzer/bin/depanalyzer.bat analyze C:/MisProyectos/JavaApp --no-color
```

## Desarrollo y Tests

Si deseas ejecutar los tests unitarios:

```bash
./gradlew test
```

## CI/CD

El proyecto cuenta con un pipeline de GitHub Actions que ejecuta los tests en cada push a `main` y pull requests.
