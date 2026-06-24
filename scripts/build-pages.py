from __future__ import annotations

import html
import shutil
import sys
from pathlib import Path

import markdown


ROOT = Path(__file__).resolve().parents[1]
DOCS_DIR = ROOT / "docs"
SITE_DIR = ROOT / "build" / "pages-site"


def title_for(path: Path) -> str:
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("# "):
            return line[2:].strip()
    return path.stem.replace("-", " ")


def render_markdown(source: Path, destination: Path) -> None:
    raw = source.read_text(encoding="utf-8")
    body = markdown.markdown(
        raw,
        extensions=["extra", "toc", "tables", "fenced_code", "sane_lists"],
        output_format="html5",
    )
    title = html.escape(title_for(source))
    relative_root = "../" * len(destination.relative_to(SITE_DIR).parents[:-1])
    stylesheet = f"{relative_root}assets/site.css"
    page = f"""<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{title}</title>
  <link rel="stylesheet" href="{stylesheet}">
</head>
<body>
  <main class="page">
    {body}
  </main>
</body>
</html>
"""
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(page, encoding="utf-8")


def copy_tree_if_exists(source: Path, destination: Path) -> None:
    if source.exists():
        if destination.exists():
            shutil.rmtree(destination)
        shutil.copytree(source, destination)


def write_status_page(destination: Path, title: str, body: str) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    index_path = destination / "index.html"
    relative_root = "../" * len(index_path.relative_to(SITE_DIR).parents[:-1])
    (destination / "index.html").write_text(
        f"""<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{html.escape(title)}</title>
  <link rel="stylesheet" href="{relative_root}assets/site.css">
</head>
<body>
  <main class="page">
    <h1>{html.escape(title)}</h1>
    <p>{html.escape(body)}</p>
  </main>
</body>
</html>
""",
        encoding="utf-8",
    )


def write_reports_index(destination: Path) -> None:
    reports = [
        ("coverage", "Cobertura JaCoCo", "Cobertura de líneas con umbral mínimo del 70%."),
        ("mutation", "Mutación PIT", "Mutation score y mutantes detectados por la suite."),
        ("bdd", "BDD Cucumber", "Escenarios ejecutables en formato Dado/Cuando/Entonces."),
        ("interface", "Interfaz Playwright", "Reporte, trazas y videos de la interfaz documental."),
        ("unit", "Pruebas unitarias", "Resultados JUnit de reglas y componentes aislados."),
        ("integration", "Pruebas de integración", "Resultados de colaboración entre módulos."),
        ("sonar", "Sonar", "Quality Gate y cobertura enviados a SonarCloud."),
        ("semgrep", "Semgrep", "Análisis estático y reporte SARIF/JSON."),
        ("snyk", "Snyk", "Análisis de vulnerabilidades de dependencias."),
    ]
    links = "\n".join(
        f'<li><a href="{slug}/">{html.escape(title)}</a>: {html.escape(description)}</li>'
        for slug, title, description in reports
    )
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "index.html").write_text(
        f"""<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Reportes de pruebas y calidad</title>
  <link rel="stylesheet" href="../assets/site.css">
</head>
<body>
  <main class="page">
    <h1>Reportes de pruebas y calidad</h1>
    <p>Índice de evidencias generadas automáticamente para la misma revisión del código.</p>
    <ul class="report-grid">{links}</ul>
  </main>
</body>
</html>
""",
        encoding="utf-8",
    )


def main() -> int:
    if SITE_DIR.exists():
        shutil.rmtree(SITE_DIR)
    SITE_DIR.mkdir(parents=True)

    assets_dir = SITE_DIR / "assets"
    assets_dir.mkdir(parents=True, exist_ok=True)
    (assets_dir / "site.css").write_text(
        """
:root { color-scheme: light; font-family: Arial, Helvetica, sans-serif; }
body { margin: 0; background: #f6f7f9; color: #20242a; }
.page { max-width: 1040px; margin: 0 auto; padding: 32px 24px 56px; background: #fff; min-height: 100vh; }
h1, h2, h3 { color: #17202a; line-height: 1.2; }
a { color: #0a58ca; }
table { border-collapse: collapse; width: 100%; margin: 16px 0; }
th, td { border: 1px solid #d6d9de; padding: 8px 10px; vertical-align: top; }
th { background: #eef1f5; }
pre { overflow: auto; background: #f0f2f5; padding: 12px; border-radius: 6px; }
code { background: #f0f2f5; padding: 1px 4px; border-radius: 4px; }
img { max-width: 100%; }
.report-grid { display: grid; gap: 12px; padding-left: 20px; }
.report-grid li { padding: 8px; }
@media (max-width: 600px) {
  .page { padding: 20px 14px 40px; overflow-wrap: anywhere; }
  table { display: block; overflow-x: auto; }
}
""".strip(),
        encoding="utf-8",
    )

    copy_tree_if_exists(DOCS_DIR / "media", SITE_DIR / "media")

    for source in DOCS_DIR.rglob("*.md"):
        rel = source.relative_to(DOCS_DIR)
        destination = SITE_DIR / rel.with_suffix(".html")
        if rel.name.lower() == "readme.md":
            destination = SITE_DIR / rel.parent / "index.html"
        render_markdown(source, destination)

    reports_root = SITE_DIR / "reports"
    write_reports_index(reports_root)
    for name in [
        "coverage",
        "sonar",
        "semgrep",
        "snyk",
        "unit",
        "integration",
        "mutation",
        "interface",
        "bdd",
    ]:
        target = reports_root / name
        if not (target / "index.html").exists():
            write_status_page(
                target,
                f"Reporte {name}",
                "Este reporte se genera durante el workflow de GitHub Pages o requiere configuración de secretos.",
            )

    api_docs = SITE_DIR / "api-docs"
    if not (api_docs / "index.html").exists():
        write_status_page(
            api_docs,
            "Documentación autogenerada",
            "La documentación Dokka se copia aquí cuando la tarea de generación finaliza correctamente.",
        )

    return 0


if __name__ == "__main__":
    sys.exit(main())
