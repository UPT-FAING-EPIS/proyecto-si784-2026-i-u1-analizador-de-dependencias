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
