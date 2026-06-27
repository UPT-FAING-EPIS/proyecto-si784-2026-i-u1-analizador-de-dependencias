import {
  buildFindingNarrative,
  canSafelyUpdate,
  changeKindLabel,
  displayVersion,
  findingTone,
  isVersionResolved,
  versionChangeKind
} from "./finding-presentation.js";
import type { Finding } from "./models.js";

export function buildFindingDetailsHtml(finding: Finding, nonce: string): string {
  const severity = finding.severity ?? (finding.kind === "outdated" ? "OUTDATED" : "UNKNOWN");
  const tone = findingTone(finding);
  const cve = finding.vulnerability?.cveId ?? "Sin CVE";
  const cvss = finding.vulnerability?.cvssScore !== undefined ? String(finding.vulnerability.cvssScore) : "N/D";
  const narrative = buildFindingNarrative(finding);
  const currentVersion = displayVersion(finding.currentVersion);
  const suggestedVersion = displayVersion(finding.latestVersion);
  const changeKind = versionChangeKind(finding.currentVersion, finding.latestVersion);
  const chain = finding.dependencyChain?.length
    ? `<div class="chain">${finding.dependencyChain.map(escapeHtml).join("<span>→</span>")}</div>`
    : `<p class="muted">No se reporto cadena transitiva para este hallazgo.</p>`;
  const referenceButton = finding.vulnerability?.referenceUrl
    ? `<button data-command="openReference">Ver CVE</button>`
    : "";
  const locationButton = finding.sourceLocation
    ? `<button data-command="openLocation">Abrir archivo</button>`
    : "";
  const updateButton = canSafelyUpdate(finding)
    ? `<button class="primary" data-command="prepareUpdate">Preparar actualizacion</button>`
    : "";
  const dynamicButton = !isVersionResolved(finding.currentVersion)
    ? `<button class="primary" data-command="enableDynamic">Activar analisis dinamico y reanalizar</button>`
    : "";
  const source = finding.vulnerability?.source ?? "DepAnalyzer";
  const location = finding.sourceLocation
    ? `${finding.sourceLocation.file}:${finding.sourceLocation.line}`
    : "Sin ubicacion exacta";
  const ecosystem = finding.ecosystem ?? "MAVEN";
  const relationship = finding.relationship === "transitive"
    ? "Transitiva"
    : finding.relationship === "direct"
      ? "Directa"
      : "No determinada";
  const directRoot = finding.directRoot ?? (finding.relationship === "direct" ? finding.coordinate : "No disponible");
  const classification = finding.chainClassification ?? "No reportada";

  return `<!doctype html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-${nonce}';">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Detalle DepAnalyzer</title>
  <style>
    body {
      --tone: #8b5cf6;
      --tone-soft: color-mix(in srgb, var(--tone) 14%, var(--vscode-editor-background));
      margin: 0;
      padding: 28px;
      color: var(--vscode-foreground);
      background: var(--vscode-editor-background);
      font-family: var(--vscode-font-family);
    }
    body[data-tone="critical"] { --tone: #ef4444; }
    body[data-tone="high"] { --tone: #f97316; }
    body[data-tone="medium"] { --tone: #eab308; }
    body[data-tone="low"] { --tone: #3b82f6; }
    body[data-tone="unknown"] { --tone: #94a3b8; }
    body[data-tone="outdated"] { --tone: #8b5cf6; }
    .shell {
      max-width: 860px;
      margin: 0 auto;
    }
    .hero {
      border: 1px solid var(--vscode-panel-border);
      border-radius: 18px;
      padding: 24px;
      border-top: 4px solid var(--tone);
      background: linear-gradient(135deg, rgba(124, 58, 237, .24), var(--tone-soft));
    }
    .eyebrow {
      color: var(--vscode-descriptionForeground);
      font-size: 12px;
      letter-spacing: .08em;
      text-transform: uppercase;
    }
    h1 {
      margin: 8px 0 12px;
      font-size: 30px;
      line-height: 1.2;
    }
    .badge {
      display: inline-block;
      padding: 5px 10px;
      border-radius: 999px;
      font-weight: 700;
      background: var(--tone);
      color: #fff;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
      gap: 12px;
      margin: 18px 0;
    }
    .metric {
      border: 1px solid var(--vscode-panel-border);
      border-radius: 14px;
      padding: 14px;
      background: var(--vscode-sideBar-background);
      border-top: 3px solid var(--tone);
    }
    .metric strong {
      display: block;
      margin-top: 6px;
      font-size: 17px;
    }
    .section {
      margin-top: 18px;
      border: 1px solid var(--vscode-panel-border);
      border-radius: 14px;
      padding: 18px;
      background: var(--vscode-editorWidget-background);
    }
    .section.accent {
      background: var(--tone-soft);
      border-left: 4px solid var(--tone);
    }
    .section h2 {
      margin-top: 0;
      font-size: 18px;
    }
    .technical {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 10px;
    }
    .technical div {
      padding: 10px 12px;
      border-radius: 10px;
      background: var(--vscode-sideBar-background);
    }
    .technical span {
      display: block;
      margin-bottom: 5px;
      color: var(--vscode-descriptionForeground);
      font-size: 12px;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      margin-top: 18px;
    }
    button {
      border: 1px solid var(--vscode-button-border, transparent);
      border-radius: 8px;
      padding: 9px 13px;
      color: var(--vscode-button-secondaryForeground);
      background: var(--vscode-button-secondaryBackground);
      cursor: pointer;
    }
    button:hover {
      background: var(--vscode-button-secondaryHoverBackground);
    }
    button.primary {
      color: var(--vscode-button-foreground);
      background: var(--vscode-button-background);
    }
    button.primary:hover {
      background: var(--vscode-button-hoverBackground);
    }
    .muted {
      color: var(--vscode-descriptionForeground);
    }
    .chain {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }
    .chain span {
      color: var(--vscode-descriptionForeground);
    }
    code {
      color: var(--vscode-textPreformat-foreground);
      background: var(--vscode-textCodeBlock-background);
      padding: 2px 5px;
      border-radius: 5px;
    }
  </style>
</head>
<body data-tone="${escapeHtml(tone)}">
  <main class="shell">
    <section class="hero">
      <div class="eyebrow">DepAnalyzer Security</div>
      <h1>${escapeHtml(finding.coordinate)}</h1>
      <span class="badge">${escapeHtml(severity)}</span>
      <div class="actions">
        ${locationButton}
        ${referenceButton}
        ${updateButton}
        ${dynamicButton}
      </div>
    </section>

    <section class="grid" aria-label="Resumen del hallazgo">
      <div class="metric"><span class="muted">CVE</span><strong>${escapeHtml(cve)}</strong></div>
      <div class="metric"><span class="muted">CVSS</span><strong>${escapeHtml(cvss)}</strong></div>
      <div class="metric"><span class="muted">Actual</span><strong>${escapeHtml(currentVersion)}</strong></div>
      <div class="metric"><span class="muted">Sugerida</span><strong>${escapeHtml(suggestedVersion)}</strong></div>
    </section>

    <section class="section accent">
      <h2>Que encontramos</h2>
      <p>${escapeHtml(narrative.summary)}</p>
    </section>

    <section class="section">
      <h2>Por que importa</h2>
      <p>${escapeHtml(narrative.impact)}</p>
    </section>

    <section class="section">
      <h2>Que recomendamos</h2>
      <p>${escapeHtml(narrative.recommendation)}</p>
    </section>

    <section class="section">
      <h2>Informacion tecnica</h2>
      <div class="technical">
        <div><span>Ecosistema</span><strong>${escapeHtml(ecosystem)}</strong></div>
        <div><span>Fuente</span><strong>${escapeHtml(source)}</strong></div>
        <div><span>Ubicacion</span><strong>${escapeHtml(location)}</strong></div>
        <div><span>Tipo de cambio</span><strong>${escapeHtml(changeKindLabel(changeKind))}</strong></div>
        <div><span>Relacion</span><strong>${escapeHtml(relationship)}</strong></div>
        <div><span>Raiz directa</span><strong>${escapeHtml(directRoot)}</strong></div>
        <div><span>Clasificacion</span><strong>${escapeHtml(classification)}</strong></div>
      </div>
    </section>

    <section class="section">
      <h2>Cadena de dependencia</h2>
      ${chain}
    </section>
  </main>

  <script nonce="${nonce}">
    const vscode = acquireVsCodeApi();
    for (const button of document.querySelectorAll("button[data-command]")) {
      button.addEventListener("click", () => vscode.postMessage({ command: button.dataset.command }));
    }
  </script>
</body>
</html>`;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}
