# language: es
Característica: Analizar proyectos y comunicar resultados verificables
  Como desarrollador que mantiene dependencias
  Quiero identificar el ecosistema y la severidad de los hallazgos
  Para decidir actualizaciones con evidencia automatizada

  Esquema del escenario: Detectar proyectos soportados
    Dado un proyecto con el archivo "<archivo>"
    Cuando DepAnalyzer detecta el tipo de proyecto
    Entonces el tipo detectado es "<tipo>"

    Ejemplos:
      | archivo             | tipo                |
      | pom.xml             | MAVEN               |
      | build.gradle        | GRADLE_GROOVY       |
      | build.gradle.kts    | GRADLE_KOTLIN       |
      | package.json        | NPM                 |
      | pyproject.toml      | PYTHON_POETRY       |
      | poetry.lock         | PYTHON_POETRY       |
      | requirements.txt    | PYTHON_REQUIREMENTS |

  Escenario: Rechazar un directorio sin manifiesto
    Dado un directorio sin archivos de dependencias
    Cuando DepAnalyzer detecta el tipo de proyecto
    Entonces se informa que el tipo de proyecto no es reconocido

  Esquema del escenario: Clasificar puntajes CVSS
    Dado un puntaje CVSS de "<puntaje>"
    Entonces la severidad calculada es "<severidad>"

    Ejemplos:
      | puntaje | severidad |
      | 10.0    | CRITICAL  |
      | 9.0     | CRITICAL  |
      | 8.9     | HIGH      |
      | 7.0     | HIGH      |
      | 6.9     | MEDIUM    |
      | 4.0     | MEDIUM    |
      | 3.9     | LOW       |
      | 0.1     | LOW       |
      | 0.0     | UNKNOWN   |

  Escenario: Clasificar un puntaje CVSS ausente
    Dado un puntaje CVSS ausente
    Entonces la severidad calculada es "UNKNOWN"

  Escenario: Mantener un contrato JSON versionado
    Dado un reporte JSON mínimo con proyecto "demo"
    Cuando el reporte es consumido por una automatización
    Entonces conserva la versión de esquema "1.0"
    Y conserva el nombre de proyecto "demo"
