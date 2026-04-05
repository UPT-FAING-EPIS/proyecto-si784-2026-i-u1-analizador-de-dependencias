# AGENTS.md - Guía para Agentes de IA

## 📋 Git Policies (CRÍTICO)

### Política de Commits

❌ **NUNCA** hagas `git add` sin autorización explícita del usuario  
❌ **NUNCA** hagas `git commit` sin autorización explícita del usuario  
✅ Puedes preparar cambios y mostrar diffs, pero espera instrucción explícita  
✅ Si el usuario dice "crea un commit" o "haz commit", entonces procede

### Política de Git Push

❌ **NUNCA** hagas `git push` sin autorización explícita del usuario  
✅ Solo haz push cuando el usuario lo pida explícitamente  
✅ Avisa al usuario si hay commits listos para ser pusheados

### Operaciones Git Permitidas

✅ **Exploración sin restricciones:**

- `git status` - Ver estado del repositorio
- `git log` - Ver histórico de commits
- `git diff` - Ver cambios
- `git branch` - Ver ramas

✅ **Cambios de rama:** Solo si el usuario lo solicita explícitamente

❌ **Operaciones destructivas:** `git reset --hard`, `git rebase -i`, `git add .`

- Solo con autorización explícita del usuario

### Commit Message Format (cuando esté autorizado)

Usar **Conventional Commits**:

- `feat:` Nueva funcionalidad
- `fix:` Corrección de bugs
- `docs:` Documentación
- `test:` Tests
- `chore:` Tareas administrativas

---

## 🏗️ Información del Proyecto

**Tipo:** Aplicación CLI en Kotlin  
**Propósito:** Analizar dependencias de proyectos Java (Maven/Gradle) para detectar versiones desactualizadas y
vulnerabilidades CVE

**Tech Stack:**

- JDK 25+ (con Kotlin)
- Build Tool: Gradle (Kotlin DSL)
- CLI Framework: Clikt 5.1.0
- Testing: JUnit 5 (Jupiter), MockK 1.14.9
- HTTP: OkHttp3 5.3.2
- JSON/XML: Jackson 3.1.0

---

## 🔨 Build & Test Commands

### Build

```bash
./gradlew build              # Compilar y construir el proyecto
./gradlew clean              # Limpiar artefactos de build
./gradlew installDist        # Crear distribución ejecutable
```

### Testing

```bash
./gradlew test               # Ejecutar todos los tests
./gradlew test --tests GradleGroovyDependencyParserTest  # Test específico
./gradlew test --tests ClassName.methodName              # Test method específico
```

### Execution

```bash
./gradlew run                # Ejecutar la aplicación
./build/install/depanalyzer/bin/depanalyzer  # Ejecutar distribución instalada
```

---

## 📝 Code Style Guidelines

### Naming Conventions

- **Clases:** `PascalCase` (ej: `ProjectAnalyzer`, `OssIndexClient`)
- **Funciones:** `camelCase` (ej: `analyze`, `getVulnerabilities`)
- **Constantes:** `UPPER_SNAKE_CASE` (ej: `BATCH_SIZE`, `MAX_RETRIES`)
- **Paquetes:** Reverse domain (ej: `com.depanalyzer.parser`)

### Imports & Formatting

- **Orden:** stdlib → third-party → proyecto
- **Kotlin Code Style:** Official (`gradle.properties: kotlin.code.style=official`)
- Preferir una línea por import
- Mantener imports organizados y sin duplicados

### Type Safety & Null Handling

- Preferir tipos no-nullable: `String` en lugar de `String?`
- Usar Elvis operator: `version ?: "unknown"`
- Safe navigation: `dependency?.version`
- `require()` para validar precondiciones: `require(path.exists()) { "Path must exist" }`

### Error Handling

- **Try-catch para I/O:** File operations, OkHttp responses
- **Throws explícitos:** `IllegalArgumentException`, `IllegalStateException`
- **Graceful degradation:** Continuar análisis incluso si OSS Index falla
- Loguear errores en stderr pero no detener la ejecución

### Patterns Comunes

- **Data classes:** Para inmutables value objects (`data class DependencyInfo(...)`)
- **Extension functions:** `OssIndexVulnerability.toVulnerability()`
- **Companion objects:** Para constantes (`companion object { const val BATCH_SIZE = 128 }`)
- **Constructor injection:** `class Analyzer(private val client: RepositoryClient = OssIndexClient())`

---

## 🧪 Testing Patterns

**Framework:** JUnit 5 (Jupiter) + Kotlin Test assertions  
**Mocking:** MockK 1.14.9 para mockear objetos  
**HTTP Mock:** OkHttp3 MockWebServer para APIs

**Test File Pattern:** `src/test/kotlin/**/*Test.kt`  
**Fixtures:** `src/test/resources/poms/` para archivos de prueba

**Estructura típica:**

```kotlin
class ParserTest {
    @Test
    fun `should parse valid dependency`() {
        // Arrange
        // Act
        // Assert
    }
}
```

---

## 🏛️ Architecture

**Módulos:**

1. **CLI** - Interfaz de línea de comandos (Clikt)
2. **Core** - Orquestación del análisis
3. **Parser** - Parseo de Maven/Gradle
4. **Repository** - Integración con APIs externas
5. **Report** - Generación de reportes

**Entry Point:** `com.depanalyzer.cli.DepAnalyzerCliKt`

**Key Classes:**

- `ProjectAnalyzer` - Orquesta el análisis completo
- `ProjectDetector` - Detecta tipo de proyecto
- `OssIndexClient` - Cliente para vulnerabilidades (batch de 128 items)
- `ConsoleRenderer` - Renderiza output en consola con colores

---

## 🔑 Configuración Requerida

### OSS_INDEX_TOKEN (Obligatorio)

El token de Sonatype OSS Index es **requerido** para análisis completos de vulnerabilidades:

- **Sin token:** ~120 solicitudes/hora (muy limitado)
- **Con token:** ~1,000+ solicitudes/hora (adecuado para desarrollo y CI/CD)

**Cómo obtenerlo:**

1. Ve a [https://guide.sonatype.com/](https://guide.sonatype.com/)
2. Sign in → Settings → Personal Access Tokens
3. Generate New Token → Copy inmediatamente

**Configuración:**

```bash
# Linux/macOS
export OSS_INDEX_TOKEN="tu_token"

# Windows PowerShell
$env:OSS_INDEX_TOKEN="tu_token"

# Windows CMD
set OSS_INDEX_TOKEN=tu_token
```

Para detalles completos, ver **README.md - Configuración del OSS_INDEX_TOKEN**

**Nota:** Los tests que usan `OssIndexClient` necesitan este token configurado para funcionar correctamente.

---

## ⚠️ Important Notes

- **No Cursor Rules:** No hay configuración en `.cursor/rules/`
- **No Copilot Instructions:** No hay `.github/copilot-instructions.md`
- **JDK Requirement:** Java 25+ requerido (Kotlin 2.1.0)
- **CI/CD:** GitHub Actions en `.github/workflows/test.yml`
- **OSS Index API:** Usa batching para eficiencia, incluye reintentos exponenciales
