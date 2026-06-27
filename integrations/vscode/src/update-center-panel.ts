import path from "node:path";
import { changeKindLabel, displayVersion, versionChangeKind } from "./finding-presentation.js";
import type { UpdatePlan, UpdateSuggestion } from "./models.js";
import {
  coordinateFor,
  isSuggestionSafe,
  sortUpdateSuggestions
} from "./update-presentation.js";

export function buildUpdateCenterHtml(
  plan: UpdatePlan,
  nonce: string,
  preselectedId?: string
): string {
  const suggestions = sortUpdateSuggestions(plan.suggestions);
  const safeCount = suggestions.filter(isSuggestionSafe).length;
  const unresolvedCount = suggestions.length - safeCount;
  const cards = suggestions.length > 0
    ? suggestions.map((suggestion) => suggestionCard(suggestion, suggestion.id === preselectedId)).join("")
    : `<section class="empty"><h2>Todo esta actualizado</h2><p>El CLI no encontro cambios editables para este proyecto.</p></section>`;

  return documentShell(
    "Centro de actualizaciones",
    nonce,
    `
      <header class="hero">
        <div>
          <span class="eyebrow">DepAnalyzer Security</span>
          <h1>Centro de actualizaciones</h1>
          <p>Selecciona explicitamente los cambios que deseas preparar. Las correcciones de seguridad aparecen primero.</p>
        </div>
        <div class="summary">
          <strong>${safeCount}</strong>
          <span>actualizaciones editables</span>
        </div>
      </header>

      <section class="plan-info">
        <div><span>Proyecto</span><strong>${escapeHtml(plan.projectType)}</strong></div>
        <div><span>Archivo afectado</span><strong>${escapeHtml(path.basename(plan.buildFile))}</strong></div>
        <div><span>Ruta</span><code>${escapeHtml(plan.buildFile)}</code></div>
      </section>

      <main class="suggestions">
        ${cards}
      </main>

      <footer class="toolbar">
        <span id="selectionCount">0 seleccionadas</span>
        <div>
          ${unresolvedCount > 0 ? `<button data-command="enableDynamic">Activar analisis dinamico</button>` : ""}
          <button data-command="reload">Actualizar plan</button>
          <button class="primary" id="applyButton" disabled>Aplicar seleccionadas</button>
        </div>
      </footer>
    `,
    `
      const checkboxes = Array.from(document.querySelectorAll('input[name="suggestion"]:not(:disabled)'));
      const count = document.getElementById("selectionCount");
      const applyButton = document.getElementById("applyButton");

      function updateSelection() {
        const selected = checkboxes.filter((checkbox) => checkbox.checked);
        count.textContent = selected.length + (selected.length === 1 ? " seleccionada" : " seleccionadas");
        applyButton.disabled = selected.length === 0;
      }

      for (const checkbox of checkboxes) checkbox.addEventListener("change", updateSelection);
      applyButton.addEventListener("click", () => {
        const ids = checkboxes.filter((checkbox) => checkbox.checked).map((checkbox) => checkbox.value);
        vscode.postMessage({ command: "apply", ids });
      });
      updateSelection();
    `
  );
}

export function buildUpdateCenterLoadingHtml(nonce: string): string {
  return documentShell(
    "Preparando actualizaciones",
    nonce,
    `
      <main class="state-card">
        <span class="state-icon loading-icon">...</span>
        <h1>Preparando actualizaciones</h1>
        <p>DepAnalyzer esta generando un plan actualizado y todavia no modificara ningun archivo.</p>
      </main>
    `
  );
}

export function buildUpdateCenterErrorHtml(message: string, nonce: string, incompatible = false): string {
  return documentShell(
    "Centro de actualizaciones",
    nonce,
    `
      <main class="state-card error-state">
        <span class="state-icon">${incompatible ? "!" : "×"}</span>
        <h1>${incompatible ? "CLI incompatible" : "No se pudo preparar el plan"}</h1>
        <p>${escapeHtml(message)}</p>
        <div class="actions">
          <button data-command="showOutput">Ver salida tecnica</button>
          ${incompatible ? `<button data-command="upgradeCli">Actualizar CLI</button>` : ""}
          <button class="primary" data-command="reload">Intentar nuevamente</button>
        </div>
      </main>
    `
  );
}

export function buildUpdateCenterSuccessHtml(
  suggestions: UpdateSuggestion[],
  nonce: string
): string {
  const rows = suggestions.map((suggestion) => `
    <li>
      <strong>${escapeHtml(coordinateFor(suggestion))}</strong>
      <span>${escapeHtml(displayVersion(suggestion.currentVersion))} → ${escapeHtml(displayVersion(suggestion.newVersion))}</span>
    </li>
  `).join("");

  return documentShell(
    "Actualizaciones procesadas",
    nonce,
    `
      <main class="state-card success-state">
        <span class="state-icon">✓</span>
        <h1>Actualizaciones procesadas</h1>
        <p>DepAnalyzer termino la operacion. Se abrira una comparacion del archivo y el workspace se analizara nuevamente.</p>
        <ul class="result-list">${rows}</ul>
        <div class="actions">
          <button data-command="showOutput">Ver salida tecnica</button>
          <button class="primary" data-command="reload">Generar un nuevo plan</button>
        </div>
      </main>
    `
  );
}

function suggestionCard(suggestion: UpdateSuggestion, preselected: boolean): string {
  const safe = isSuggestionSafe(suggestion);
  const security = suggestion.reason.toUpperCase() === "CVE";
  const changeKind = versionChangeKind(suggestion.currentVersion, suggestion.newVersion);
  const warning = safe
    ? changeKind === "major"
      ? "Cambio mayor: revisa la guia de migracion y ejecuta todas las pruebas."
      : "Revisa el changelog y valida el proyecto despues del cambio."
    : "La version actual no fue detectada. Activa el analisis dinamico antes de actualizar.";
  const via = suggestion.viaDirectCoordinate
    ? `<span class="meta">Via ${escapeHtml(suggestion.viaDirectCoordinate)}</span>`
    : "";

  return `
    <article class="suggestion ${security ? "security" : "outdated"} ${safe ? "" : "disabled"}">
      <label>
        <input
          type="checkbox"
          name="suggestion"
          value="${escapeHtml(suggestion.id)}"
          ${preselected && safe ? "checked" : ""}
          ${safe ? "" : "disabled"}
        >
        <span class="checkmark"></span>
        <span class="content">
          <span class="title-row">
            <strong>${escapeHtml(coordinateFor(suggestion))}</strong>
            <span class="badge">${security ? "SEGURIDAD" : "DESACTUALIZADA"}</span>
          </span>
          <span class="versions">
            ${escapeHtml(displayVersion(suggestion.currentVersion))}
            <span>→</span>
            ${escapeHtml(displayVersion(suggestion.newVersion))}
          </span>
          <span class="metadata">
            <span class="meta">${escapeHtml(changeKindLabel(changeKind))}</span>
            <span class="meta">${escapeHtml(targetLabel(suggestion.targetType))}</span>
            <span class="meta">${escapeHtml(suggestion.ecosystem)}</span>
            ${via}
          </span>
          <span class="warning">${escapeHtml(warning)}</span>
        </span>
      </label>
    </article>
  `;
}

function targetLabel(targetType: string): string {
  return targetType === "TRANSITIVE_OVERRIDE" ? "Override transitivo" : "Dependencia directa";
}

function documentShell(title: string, nonce: string, content: string, extraScript = ""): string {
  return `<!doctype html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-${nonce}';">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${escapeHtml(title)}</title>
  <style>
    * { box-sizing: border-box; }
    body {
      margin: 0;
      padding: 28px;
      color: var(--vscode-foreground);
      background: var(--vscode-editor-background);
      font-family: var(--vscode-font-family);
    }
    body > * { max-width: 980px; margin-left: auto; margin-right: auto; }
    .hero {
      display: flex;
      justify-content: space-between;
      gap: 24px;
      padding: 26px;
      border: 1px solid var(--vscode-panel-border);
      border-top: 4px solid #8b5cf6;
      border-radius: 18px;
      background: linear-gradient(135deg, rgba(124, 58, 237, .24), rgba(37, 99, 235, .12));
    }
    .eyebrow {
      color: var(--vscode-descriptionForeground);
      font-size: 12px;
      letter-spacing: .08em;
      text-transform: uppercase;
    }
    h1 { margin: 8px 0; }
    .hero p { max-width: 650px; margin-bottom: 0; color: var(--vscode-descriptionForeground); }
    .summary {
      min-width: 150px;
      padding: 16px;
      border-radius: 14px;
      text-align: center;
      background: color-mix(in srgb, #8b5cf6 18%, var(--vscode-editor-background));
    }
    .summary strong { display: block; font-size: 30px; }
    .summary span { color: var(--vscode-descriptionForeground); }
    .plan-info {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 10px;
      margin-top: 16px;
    }
    .plan-info div {
      min-width: 0;
      padding: 12px;
      border: 1px solid var(--vscode-panel-border);
      border-radius: 10px;
      background: var(--vscode-sideBar-background);
    }
    .plan-info span { display: block; margin-bottom: 5px; color: var(--vscode-descriptionForeground); font-size: 12px; }
    .plan-info code { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .suggestions { margin-top: 18px; padding-bottom: 84px; }
    .suggestion {
      margin-bottom: 12px;
      border: 1px solid var(--vscode-panel-border);
      border-left: 4px solid #8b5cf6;
      border-radius: 14px;
      background: var(--vscode-editorWidget-background);
    }
    .suggestion.security { border-left-color: #ef4444; }
    .suggestion.disabled { opacity: .72; }
    .suggestion label { display: flex; gap: 14px; padding: 17px; cursor: pointer; }
    .suggestion.disabled label { cursor: not-allowed; }
    input[type="checkbox"] { width: 18px; height: 18px; accent-color: #8b5cf6; }
    .content { display: block; flex: 1; min-width: 0; }
    .title-row { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 10px; }
    .title-row strong { font-size: 16px; word-break: break-word; }
    .badge {
      padding: 3px 8px;
      border-radius: 999px;
      color: #fff;
      background: #8b5cf6;
      font-size: 11px;
      font-weight: 700;
    }
    .security .badge { background: #ef4444; }
    .versions { display: block; margin-top: 10px; font-size: 15px; font-weight: 600; }
    .versions span { margin: 0 7px; color: var(--vscode-descriptionForeground); }
    .metadata { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 10px; }
    .meta {
      padding: 4px 7px;
      border-radius: 6px;
      color: var(--vscode-descriptionForeground);
      background: var(--vscode-textCodeBlock-background);
      font-size: 12px;
    }
    .warning { display: block; margin-top: 10px; color: var(--vscode-descriptionForeground); }
    .toolbar {
      position: fixed;
      right: 28px;
      bottom: 20px;
      left: 28px;
      z-index: 2;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      max-width: 980px;
      margin: auto;
      padding: 13px 16px;
      border: 1px solid var(--vscode-panel-border);
      border-radius: 12px;
      background: var(--vscode-editorWidget-background);
      box-shadow: 0 8px 24px rgba(0, 0, 0, .22);
    }
    .toolbar div, .actions { display: flex; flex-wrap: wrap; gap: 9px; }
    button {
      border: 1px solid var(--vscode-button-border, transparent);
      border-radius: 8px;
      padding: 9px 13px;
      color: var(--vscode-button-secondaryForeground);
      background: var(--vscode-button-secondaryBackground);
      cursor: pointer;
    }
    button:hover { background: var(--vscode-button-secondaryHoverBackground); }
    button.primary { color: var(--vscode-button-foreground); background: var(--vscode-button-background); }
    button.primary:hover { background: var(--vscode-button-hoverBackground); }
    button:disabled { cursor: not-allowed; opacity: .5; }
    .empty, .state-card {
      margin-top: 50px;
      padding: 30px;
      border: 1px solid var(--vscode-panel-border);
      border-radius: 18px;
      text-align: center;
      background: var(--vscode-editorWidget-background);
    }
    .state-icon {
      display: inline-grid;
      width: 54px;
      height: 54px;
      place-items: center;
      border-radius: 50%;
      color: #fff;
      background: #ef4444;
      font-size: 28px;
      font-weight: 700;
    }
    .success-state .state-icon { background: #22c55e; }
    .loading-icon { background: #8b5cf6; font-size: 16px; }
    .state-card p { color: var(--vscode-descriptionForeground); }
    .state-card .actions { justify-content: center; margin-top: 20px; }
    .result-list { max-width: 620px; margin: 22px auto; padding: 0; list-style: none; text-align: left; }
    .result-list li {
      display: flex;
      justify-content: space-between;
      gap: 15px;
      padding: 10px 0;
      border-bottom: 1px solid var(--vscode-panel-border);
    }
    @media (max-width: 620px) {
      body { padding: 16px; }
      .hero { flex-direction: column; }
      .toolbar { right: 16px; left: 16px; flex-direction: column; align-items: stretch; }
      .result-list li { flex-direction: column; }
    }
  </style>
</head>
<body>
  ${content}
  <script nonce="${nonce}">
    const vscode = acquireVsCodeApi();
    for (const button of document.querySelectorAll("button[data-command]")) {
      button.addEventListener("click", () => vscode.postMessage({ command: button.dataset.command }));
    }
    ${extraScript}
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
