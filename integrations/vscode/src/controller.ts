import path from "node:path";
import * as vscode from "vscode";
import { DepAnalyzerCli } from "./cli.js";
import { FindingsProvider } from "./findings-view.js";
import type { Finding, UpdateCandidate } from "./models.js";
import { flattenFindings, isSupportedDependencyFile, keyFor } from "./report-utils.js";

interface StoredFinding {
  finding: Finding;
  range: vscode.Range;
  projectPath: string;
}

export class DepAnalyzerController implements vscode.HoverProvider, vscode.CodeActionProvider {
  private readonly diagnostics = vscode.languages.createDiagnosticCollection("depanalyzer");
  private readonly storedFindings = new Map<string, StoredFinding[]>();
  private lastProjectPath: string | undefined;

  constructor(
    private readonly cli: DepAnalyzerCli,
    private readonly findingsProvider: FindingsProvider,
    private readonly output: vscode.OutputChannel
  ) {}

  dispose(): void {
    this.diagnostics.dispose();
  }

  async analyzeWorkspace(): Promise<void> {
    const folder = vscode.workspace.workspaceFolders?.[0];
    if (!folder) {
      void vscode.window.showWarningMessage("DepAnalyzer necesita un workspace abierto.");
      return;
    }
    await this.analyzeProject(folder.uri.fsPath);
  }

  async analyzeDocument(document: vscode.TextDocument): Promise<void> {
    if (!isSupportedDependencyFile(document.fileName)) return;
    const folder = vscode.workspace.getWorkspaceFolder(document.uri);
    await this.analyzeProject(folder?.uri.fsPath ?? path.dirname(document.fileName));
  }

  clear(): void {
    this.diagnostics.clear();
    this.storedFindings.clear();
    this.findingsProvider.clear();
  }

  provideHover(
    document: vscode.TextDocument,
    position: vscode.Position
  ): vscode.ProviderResult<vscode.Hover> {
    const findings = this.findingsAt(document.uri, position);
    if (findings.length === 0) return undefined;

    const markdown = new vscode.MarkdownString(undefined, true);
    markdown.isTrusted = false;
    for (const stored of findings) {
      markdown.appendMarkdown(formatFindingMarkdown(stored.finding));
      markdown.appendMarkdown("\n\n---\n\n");
    }
    return new vscode.Hover(markdown, findings[0]?.range);
  }

  provideCodeActions(
    document: vscode.TextDocument,
    range: vscode.Range
  ): vscode.ProviderResult<vscode.CodeAction[]> {
    const actions: vscode.CodeAction[] = [];
    for (const stored of this.findingsAt(document.uri, range.start)) {
      const finding = stored.finding;
      if (!finding.latestVersion) continue;

      const action = new vscode.CodeAction(
        `Actualizar ${finding.coordinate} a ${finding.latestVersion}`,
        vscode.CodeActionKind.QuickFix
      );
      action.command = {
        command: "depanalyzer.applyUpdate",
        title: action.title,
        arguments: [{
          projectPath: stored.projectPath,
          groupId: finding.groupId,
          artifactId: finding.artifactId,
          currentVersion: finding.currentVersion,
          newVersion: finding.latestVersion,
          ecosystem: finding.ecosystem
        } satisfies UpdateCandidate]
      };
      action.diagnostics = (this.diagnostics.get(document.uri) ?? [])
        .filter((diagnostic) => diagnostic.range.intersection(range));
      actions.push(action);
    }
    return actions;
  }

  async applyUpdate(candidate: UpdateCandidate): Promise<void> {
    const choice = await vscode.window.showWarningMessage(
      `Aplicar actualizacion ${candidate.groupId}:${candidate.artifactId} ${candidate.currentVersion} -> ${candidate.newVersion}?`,
      { modal: true },
      "Aplicar"
    );
    if (choice !== "Aplicar") return;

    const plan = await vscode.window.withProgress(
      { location: vscode.ProgressLocation.Notification, title: "DepAnalyzer: preparando actualizacion" },
      () => this.cli.planUpdates(candidate.projectPath)
    );
    const suggestion = plan.suggestions.find((item) =>
      item.groupId === candidate.groupId &&
      item.artifactId === candidate.artifactId &&
      item.currentVersion === candidate.currentVersion &&
      item.newVersion === candidate.newVersion &&
      (!candidate.ecosystem || item.ecosystem === candidate.ecosystem)
    );
    if (!suggestion) {
      void vscode.window.showWarningMessage("La sugerencia ya no esta disponible. Reejecuta el analisis.");
      return;
    }

    const output = await vscode.window.withProgress(
      { location: vscode.ProgressLocation.Notification, title: "DepAnalyzer: aplicando actualizacion" },
      () => this.cli.applyUpdate(candidate.projectPath, suggestion.id)
    );
    this.output.appendLine(output);
    void vscode.window.showInformationMessage("Actualizacion aplicada por DepAnalyzer.");
    await this.analyzeProject(candidate.projectPath);
  }

  private async analyzeProject(projectPath: string): Promise<void> {
    this.lastProjectPath = projectPath;
    try {
      const report = await vscode.window.withProgress(
        { location: vscode.ProgressLocation.Notification, title: "DepAnalyzer: analizando dependencias" },
        () => this.cli.analyze(projectPath)
      );
      const findings = flattenFindings(report);
      this.updateDiagnostics(projectPath, findings);
      this.findingsProvider.refresh(findings);
      void vscode.window.setStatusBarMessage(`DepAnalyzer: ${findings.length} hallazgos`, 5000);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      this.output.appendLine(message);
      void vscode.window.showErrorMessage(`DepAnalyzer: ${message}`);
    }
  }

  private updateDiagnostics(projectPath: string, findings: Finding[]): void {
    this.diagnostics.clear();
    this.storedFindings.clear();

    const byUri = new Map<string, { uri: vscode.Uri; diagnostics: vscode.Diagnostic[]; stored: StoredFinding[] }>();
    for (const finding of findings) {
      if (!finding.sourceLocation) continue;
      const uri = vscode.Uri.file(path.join(projectPath, finding.sourceLocation.file));
      const range = new vscode.Range(
        finding.sourceLocation.line - 1,
        finding.sourceLocation.startColumn - 1,
        finding.sourceLocation.line - 1,
        Math.max(finding.sourceLocation.startColumn, finding.sourceLocation.endColumn - 1)
      );
      const diagnostic = new vscode.Diagnostic(range, diagnosticMessage(finding), diagnosticSeverity(finding));
      diagnostic.source = "DepAnalyzer";
      diagnostic.code = finding.vulnerability?.referenceUrl
        ? { value: finding.vulnerability.cveId, target: vscode.Uri.parse(finding.vulnerability.referenceUrl) }
        : finding.vulnerability?.cveId;

      const key = uri.toString();
      const bucket = byUri.get(key) ?? { uri, diagnostics: [], stored: [] };
      bucket.diagnostics.push(diagnostic);
      bucket.stored.push({ finding, range, projectPath: this.lastProjectPath ?? projectPath });
      byUri.set(key, bucket);
    }

    for (const [key, value] of byUri) {
      this.diagnostics.set(value.uri, value.diagnostics);
      this.storedFindings.set(key, value.stored);
    }
  }

  private findingsAt(uri: vscode.Uri, position: vscode.Position): StoredFinding[] {
    return (this.storedFindings.get(uri.toString()) ?? [])
      .filter((stored) => stored.range.contains(position));
  }
}

function diagnosticMessage(finding: Finding): string {
  if (finding.kind === "vulnerability") {
    const vuln = finding.vulnerability;
    const cvss = vuln?.cvssScore !== undefined ? ` CVSS ${vuln.cvssScore}` : "";
    return `${finding.coordinate} vulnerable: ${vuln?.cveId ?? "CVE"} ${finding.severity ?? "UNKNOWN"}${cvss}`;
  }
  return `${finding.coordinate} desactualizada: ${finding.currentVersion} -> ${finding.latestVersion}`;
}

function diagnosticSeverity(finding: Finding): vscode.DiagnosticSeverity {
  if (finding.kind === "outdated") return vscode.DiagnosticSeverity.Information;
  if (finding.severity === "CRITICAL" || finding.severity === "HIGH") return vscode.DiagnosticSeverity.Error;
  if (finding.severity === "MEDIUM") return vscode.DiagnosticSeverity.Warning;
  return vscode.DiagnosticSeverity.Information;
}

function formatFindingMarkdown(finding: Finding): string {
  if (finding.kind === "outdated") {
    return `**${finding.coordinate}** esta desactualizada\n\nVersion actual: \`${finding.currentVersion}\`\n\nVersion disponible: \`${finding.latestVersion}\``;
  }
  const vuln = finding.vulnerability;
  const lines = [
    `**${finding.coordinate}** tiene ${vuln?.cveId ?? "una vulnerabilidad"}`,
    "",
    `Severidad: **${finding.severity ?? "UNKNOWN"}**`,
    vuln?.cvssScore !== undefined ? `CVSS: **${vuln.cvssScore}**` : undefined,
    finding.latestVersion ? `Actualizacion sugerida: \`${finding.latestVersion}\`` : undefined,
    vuln?.description ? `\n${vuln.description}` : undefined,
    vuln?.referenceUrl ? `\n[Ver referencia](${vuln.referenceUrl})` : undefined
  ];
  return lines.filter(Boolean).join("\n\n");
}
