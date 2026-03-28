# Analizador de Dependencias Java

Herramienta de código abierto desarrollada en Kotlin que analiza proyectos Java (Maven/Gradle), detecta dependencias desactualizadas y vulnerabilidades de seguridad (CVEs).

## Características
- Soporte para Maven (`pom.xml`), Gradle Groovy (`build.gradle`) y Gradle Kotlin (`build.gradle.kts`).
- Detección automática del tipo de proyecto.
- Resolución de repositorios del proyecto.
- Análisis de CVEs (Sonatype OSS Index).
- Sugerencias de actualización interactivas.

## Requisitos
- JDK 25 o superior.

## Construcción y Tests
Para construir el proyecto y ejecutar las pruebas:
```bash
./gradlew build
```

Para ejecutar solo los tests:
```bash
./gradlew test
```

## CI/CD
El proyecto cuenta con un pipeline de GitHub Actions que ejecuta los tests en cada push a `main` y pull requests.
