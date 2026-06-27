import type { Finding } from "./models.js";

export function buildFindingDetailsHtml(finding: Finding, nonce: string, canUpdate: boolean): string {
  const severity = finding.severity ?? (finding.kind === "outdated" ? "OUTDATED" : "UNKNOWN");
  const cve = finding.vulnerability?.cveId ?? "Sin CVE";
  const cvss = finding.vulnerability?.cvssScore !== undefined ? String(finding.vulnerability.cvssScore) : "N/D";
  const description = finding.vulnerability?.description?.trim() || defaultDescription(finding);
  const chain = finding.dependencyChain?.length
    ? `<div class="chain">${finding.dependencyChain.map(escapeHtml).join("<span>→</span>")}</div>`
    : `<p class="muted">No se reporto cadena transitiva para este hallazgo.</p>`;
  const referenceButton = finding.vulnerability?.referenceUrl
    ? `<button data-command="openReference">Ver CVE</button>`
    : "";
  const locationButton = finding.sourceLocation
    ? `<button data-command="openLocation">Abrir archivo</button>`
    : "";
  const updateButton = canUpdate
    ? `<button class="primary" data-command="applyUpdate">Aplicar actualizacion</button>`
    : "";

  return `<!doctype html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-${nonce}';">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Detalle DepAnalyzer</title>
  <style>
    body {
      margin: 0;
      padding: 28px;
      color: var(--vscode-foreground);
      background: var(--vscode-editor-background);
      font-family: var(--vscode-font-family);
    }
    .shell {
      max-width: 860px;
      margin: 0 auto;
    }
    .hero {
      border: 1px solid var(--vscode-panel-border);
      border-radius: 18px;
      padding: 24px;
      background: linear-gradient(135deg, rgba(124, 58, 237, .22), rgba(37, 99, 235, .12));
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
      background: var(--vscode-badge-background);
      color: var(--vscode-badge-foreground);
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
<body>
  <main class="shell">
    <section class="hero">
      <div class="eyebrow">DepAnalyzer Security</div>
      <h1>${escapeHtml(finding.coordinate)}</h1>
      <span class="badge">${escapeHtml(severity)}</span>
      <div class="actions">
        ${locationButton}
        ${referenceButton}
        ${updateButton}
      </div>
    </section>

    <section class="grid" aria-label="Resumen del hallazgo">
      <div class="metric"><span class="muted">CVE</span><strong>${escapeHtml(cve)}</strong></div>
      <div class="metric"><span class="muted">CVSS</span><strong>${escapeHtml(cvss)}</strong></div>
      <div class="metric"><span class="muted">Actual</span><strong>${escapeHtml(finding.currentVersion)}</strong></div>
      <div class="metric"><span class="muted">Sugerida</span><strong>${escapeHtml(finding.latestVersion ?? "N/D")}</strong></div>
    </section>

    <section class="section">
      <h2>Descripcion</h2>
      <p>${escapeHtml(description)}</p>
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

function defaultDescription(finding: Finding): string {
  if (finding.kind === "outdated") {
    return `La dependencia puede actualizarse de ${finding.currentVersion} a ${finding.latestVersion ?? "una version mas reciente"}.`;
  }
  return "DepAnalyzer detecto una vulnerabilidad en esta dependencia. Revisa la referencia y prioriza la actualizacion.";
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}
