import { randomBytes } from "node:crypto";
import path from "node:path";
import * as vscode from "vscode";
import type { CliCapabilities } from "./cli-capabilities.js";
import { DepAnalyzerCli } from "./cli.js";
import { DependencyTreeProvider } from "./dependency-tree-view.js";
import { canSafelyUpdate, displayVersion } from "./finding-presentation.js";
import { FindingsProvider } from "./findings-view.js";
import { buildFindingDetailsHtml } from "./finding-details-panel.js";
import type {
  DependencyReport,
  Finding,
  FindingCommandArg,
  UpdateCandidate,
  UpdatePlan,
  UpdateSuggestion
} from "./models.js";
import { flattenFindings, isSupportedDependencyFile, keyFor } from "./report-utils.js";
import {
  buildUpdateCenterErrorHtml,
  buildUpdateCenterHtml,
  buildUpdateCenterLoadingHtml,
  buildUpdateCenterSuccessHtml
} from "./update-center-panel.js";
import {
  coordinateFor,
  findMatchingSuggestion,
  isSuggestionSafe
} from "./update-presentation.js";

interface StoredFinding {
  finding: Finding;
  range: vscode.Range;
  projectPath: string;
}

interface ProjectAnalysisResult {
  report: DependencyReport;
  findings: Finding[];
  capabilities: CliCapabilities;
}

export class DepAnalyzerController implements vscode.HoverProvider, vscode.CodeActionProvider {
  private readonly diagnostics = vscode.languages.createDiagnosticCollection("depanalyzer");
  private readonly storedFindings = new Map<string, StoredFinding[]>();
  private readonly results = new Map<string, ProjectAnalysisResult>();
  private readonly activeRuns = new Map<string, { generation: number; cancellation: vscode.CancellationTokenSource }>();
  private readonly saveTimers = new Map<string, ReturnType<typeof setTimeout>>();
  private generation = 0;
  private lastProjectPath: string | undefined;

  constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly cli: DepAnalyzerCli,
    private readonly findingsProvider: FindingsProvider,
    private readonly dependencyTreeProvider: DependencyTreeProvider,
    private readonly output: vscode.OutputChannel
  ) {}

  dispose(): void {
    for (const run of this.activeRuns.values()) run.cancellation.cancel();
    for (const timer of this.saveTimers.values()) clearTimeout(timer);
    this.diagnostics.dispose();
  }

  async analyzeWorkspace(): Promise<void> {
    const folders = vscode.workspace.workspaceFolders ?? [];
    if (folders.length === 0) {
      void vscode.window.showWarningMessage("DepAnalyzer necesita un workspace abierto.");
      return;
    }
    await Promise.all(folders.map((folder) => this.analyzeProject(folder.uri.fsPath)));
  }

  async analyzeDocument(document: vscode.TextDocument): Promise<void> {
    if (!isSupportedDependencyFile(document.fileName)) return;
    const folder = vscode.workspace.getWorkspaceFolder(document.uri);
    await this.analyzeProject(folder?.uri.fsPath ?? path.dirname(document.fileName));
  }

  scheduleDocumentAnalysis(document: vscode.TextDocument): void {
    if (!isSupportedDependencyFile(document.fileName)) return;
    const folder = vscode.workspace.getWorkspaceFolder(document.uri);
    const projectPath = folder?.uri.fsPath ?? path.dirname(document.fileName);
    const existing = this.saveTimers.get(projectPath);
    if (existing) clearTimeout(existing);
    this.saveTimers.set(projectPath, setTimeout(() => {
      this.saveTimers.delete(projectPath);
      void this.analyzeProject(projectPath);
    }, 750));
  }

  clear(): void {
    this.diagnostics.clear();
    this.storedFindings.clear();
    this.results.clear();
    this.findingsProvider.clear();
    this.dependencyTreeProvider.clear();
  }

  showOutput(): void {
    this.output.show();
  }

  resetCliCapabilities(notify = true): void {
    this.cli.resetCapabilities();
    if (notify) {
      void vscode.window.showInformationMessage("DepAnalyzer volvera a detectar el CLI en el siguiente analisis.");
    }
  }

  async guideCliUpgrade(): Promise<void> {
    const executable = (await this.cli.executablePath(this.lastProjectPath)).toLowerCase();
    const command = executable.includes(`${path.sep}scoop${path.sep}`)
      ? "scoop update depanalyzer"
      : executable.includes("homebrew") || executable.includes("/brew")
        ? "brew upgrade depanalyzer"
        : executable.includes("snap")
          ? "sudo snap refresh depanalyzer"
          : "";
    if (!command) {
      void vscode.window.showInformationMessage(
        "Actualiza DepAnalyzer CLI desde GitHub Releases y luego ejecuta 'Volver a detectar CLI'."
      );
      return;
    }
    const choice = await vscode.window.showInformationMessage(
      `CLI antiguo detectado. Comando recomendado: ${command}`,
      "Copiar comando",
      "Abrir terminal"
    );
    if (choice === "Copiar comando") await vscode.env.clipboard.writeText(command);
    if (choice === "Abrir terminal") {
      const terminal = vscode.window.createTerminal("Actualizar DepAnalyzer");
      terminal.show();
      terminal.sendText(command, false);
    }
  }

  async openFindingLocation(arg?: FindingCommandArg): Promise<void> {
    if (!arg?.finding.sourceLocation) {
      void vscode.window.showInformationMessage("Este hallazgo no tiene una ubicacion exacta en archivo.");
      return;
    }
    const location = arg.finding.sourceLocation;
    const uri = vscode.Uri.file(path.join(arg.projectPath, location.file));
    const document = await vscode.workspace.openTextDocument(uri);
    const editor = await vscode.window.showTextDocument(document);
    const range = new vscode.Range(
      location.line - 1,
      location.startColumn - 1,
      location.line - 1,
      Math.max(location.startColumn, location.endColumn - 1)
    );
    editor.selection = new vscode.Selection(range.start, range.end);
    editor.revealRange(range, vscode.TextEditorRevealType.InCenter);
  }

  async openFindingReference(arg?: FindingCommandArg): Promise<void> {
    const url = arg?.finding.vulnerability?.referenceUrl;
    if (!url) {
      void vscode.window.showInformationMessage("Este hallazgo no incluye una referencia externa.");
      return;
    }
    await vscode.env.openExternal(vscode.Uri.parse(url));
  }

  showFindingDetails(arg?: FindingCommandArg): void {
    if (!arg) {
      void vscode.window.showWarningMessage("Selecciona un hallazgo de DepAnalyzer para ver el detalle.");
      return;
    }
    const panel = vscode.window.createWebviewPanel(
      "depanalyzer.findingDetails",
      `DepAnalyzer: ${arg.finding.coordinate}`,
      vscode.ViewColumn.Beside,
      { enableScripts: true }
    );
    const nonce = createNonce();
    panel.webview.html = buildFindingDetailsHtml(arg.finding, nonce);
    panel.webview.onDidReceiveMessage((message: { command?: string }) => {
      if (message.command === "openLocation") void this.openFindingLocation(arg);
      if (message.command === "openReference") void this.openFindingReference(arg);
      if (message.command === "prepareUpdate") void this.manageUpdates(arg);
      if (message.command === "enableDynamic") {
        void this.enableDynamicAndReanalyze(arg.projectPath).then((changed) => {
          if (changed) panel.dispose();
        });
      }
    });
  }

  async manageUpdates(candidateOrArg?: UpdateCandidate | FindingCommandArg): Promise<void> {
    const projectPath = candidateOrArg?.projectPath ??
      this.lastProjectPath ??
      vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
    if (!projectPath) {
      void vscode.window.showWarningMessage("DepAnalyzer necesita un workspace abierto.");
      return;
    }

    const candidate = toUpdateCandidate(candidateOrArg);
    const panel = vscode.window.createWebviewPanel(
      "depanalyzer.updateCenter",
      "DepAnalyzer: Centro de actualizaciones",
      vscode.ViewColumn.Beside,
      { enableScripts: true }
    );
    let currentPlan: UpdatePlan | undefined;
    let currentDynamic = false;

    const renderPlan = async (): Promise<void> => {
      panel.webview.html = buildUpdateCenterLoadingHtml(createNonce());
      currentPlan = undefined;
      try {
        const dynamic = await this.resolvePreciseMode(projectPath);
        if (dynamic === undefined) {
          panel.dispose();
          return;
        }
        currentDynamic = dynamic;
        const capabilities = await this.cli.capabilitiesFor(projectPath);
        if (!capabilities.updatePlan || !capabilities.applyById) {
          panel.webview.html = buildUpdateCenterErrorHtml(
            "La version instalada del CLI no permite preparar y aplicar cambios seguros por identificador. Actualiza DepAnalyzer CLI y vuelve a intentarlo.",
            createNonce(),
            true
          );
          return;
        }

        const plan = await vscode.window.withProgress(
          {
            location: vscode.ProgressLocation.Notification,
            title: "DepAnalyzer: preparando centro de actualizaciones"
          },
          () => this.cli.planUpdates(projectPath, currentDynamic)
        );
        currentPlan = plan;
        const preselected = findMatchingSuggestion(plan.suggestions, candidate);
        panel.webview.html = buildUpdateCenterHtml(plan, createNonce(), preselected?.id);
      } catch (error) {
        const message = errorMessage(error);
        this.output.appendLine(message);
        panel.webview.html = buildUpdateCenterErrorHtml(message, createNonce());
      }
    };

    panel.webview.onDidReceiveMessage(async (message: UpdateCenterMessage) => {
      if (message.command === "showOutput") {
        this.showOutput();
        return;
      }
      if (message.command === "upgradeCli") {
        await this.guideCliUpgrade();
        return;
      }
      if (message.command === "reload") {
        await renderPlan();
        return;
      }
      if (message.command === "enableDynamic") {
        const changed = await this.enableDynamicAndReanalyze(projectPath);
        if (changed) await renderPlan();
        return;
      }
      if (message.command === "apply" && currentPlan) {
        await this.applySelectedUpdates(panel, projectPath, currentPlan, message.ids, currentDynamic);
      }
    });

    await renderPlan();
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
      if (!canSafelyUpdate(finding)) continue;

      const action = new vscode.CodeAction(
        `Actualizar ${finding.coordinate} a ${finding.latestVersion}`,
        vscode.CodeActionKind.QuickFix
      );
      action.command = {
        command: "depanalyzer.manageUpdates",
        title: action.title,
        arguments: [{
          projectPath: stored.projectPath,
          groupId: finding.groupId,
          artifactId: finding.artifactId,
          currentVersion: finding.currentVersion,
          newVersion: finding.latestVersion!,
          ecosystem: finding.ecosystem
        } satisfies UpdateCandidate]
      };
      action.diagnostics = (this.diagnostics.get(document.uri) ?? [])
        .filter((diagnostic) => diagnostic.range.intersection(range));
      actions.push(action);
    }
    return actions;
  }

  async applyUpdate(candidateOrArg?: UpdateCandidate | FindingCommandArg): Promise<void> {
    await this.manageUpdates(candidateOrArg);
  }

  private async analyzeProject(projectPath: string): Promise<void> {
    this.lastProjectPath = projectPath;
    const previous = this.activeRuns.get(projectPath);
    previous?.cancellation.cancel();
    previous?.cancellation.dispose();
    const generation = ++this.generation;
    const cancellation = new vscode.CancellationTokenSource();
    this.activeRuns.set(projectPath, { generation, cancellation });

    try {
      const dynamic = await this.resolvePreciseMode(projectPath);
      if (dynamic === undefined) return;
      const timeoutSeconds = vscode.workspace
        .getConfiguration("depanalyzer", vscode.Uri.file(projectPath))
        .get<number>("timeoutSeconds", 1800);
      const capabilities = await this.cli.capabilitiesFor(projectPath);
      let lastProgress = 0;
      const report = await vscode.window.withProgress(
        {
          location: vscode.ProgressLocation.Notification,
          title: `DepAnalyzer: ${path.basename(projectPath)}`,
          cancellable: true
        },
        (progress, uiCancellation) => {
          const subscription = uiCancellation.onCancellationRequested(() => cancellation.cancel());
          return this.cli.analyze(projectPath, {
            dynamic,
            includeChains: true,
            timeoutSeconds,
            cancellationToken: cancellation.token,
            onProgress: (event) => {
              const percentage = event.current !== undefined && event.total
                ? Math.round((event.current / event.total) * 100)
                : lastProgress;
              const increment = Math.max(0, percentage - lastProgress);
              lastProgress = percentage;
              progress.report({ message: event.message, increment });
            }
          }).finally(() => subscription.dispose());
        }
      );
      if (this.activeRuns.get(projectPath)?.generation !== generation) return;
      const findings = flattenFindings(report, projectPath);
      this.results.set(projectPath, { report, findings, capabilities });
      this.rebuildDiagnostics();
      this.findingsProvider.refresh(findings, projectPath, report, capabilities);
      this.dependencyTreeProvider.refresh(report, projectPath);
      void vscode.window.setStatusBarMessage(`DepAnalyzer: ${findings.length} hallazgos`, 5000);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (/cancelad/i.test(message)) {
        void vscode.window.setStatusBarMessage("DepAnalyzer: analisis cancelado", 3000);
        return;
      }
      this.output.appendLine(message);
      this.findingsProvider.showError(message, projectPath);
      this.dependencyTreeProvider.remove(projectPath);
      void vscode.window.showErrorMessage(`DepAnalyzer: ${message}`);
    } finally {
      if (this.activeRuns.get(projectPath)?.generation === generation) {
        this.activeRuns.delete(projectPath);
      }
      cancellation.dispose();
    }
  }

  private rebuildDiagnostics(): void {
    this.diagnostics.clear();
    this.storedFindings.clear();

    const byUri = new Map<string, { uri: vscode.Uri; diagnostics: vscode.Diagnostic[]; stored: StoredFinding[] }>();
    for (const [projectPath, result] of this.results) {
      for (const finding of result.findings) {
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
        bucket.stored.push({ finding, range, projectPath });
        byUri.set(key, bucket);
      }
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

  private async resolvePreciseMode(projectPath: string): Promise<boolean | undefined> {
    const projectUri = vscode.Uri.file(projectPath);
    const folder = vscode.workspace.getWorkspaceFolder(projectUri);
    const config = vscode.workspace.getConfiguration("depanalyzer", folder?.uri);
    const hasJvmBuild = await hasAnyFile(projectUri, [
      "pom.xml",
      "build.gradle",
      "build.gradle.kts"
    ]);
    if (!hasJvmBuild) return false;

    const configuredMode = config.get<string>("analysisMode", "ask");
    if (configuredMode === "precise") {
      if (!vscode.workspace.isTrusted) {
        void vscode.window.showWarningMessage(
          "DepAnalyzer uso analisis rapido porque el workspace no es confiable."
        );
        return false;
      }
      return true;
    }
    if (configuredMode === "fast") return false;

    const legacy = config.inspect<boolean>("dynamic");
    const legacyValue = legacy?.workspaceFolderValue ?? legacy?.workspaceValue;
    if (legacyValue === true && vscode.workspace.isTrusted) {
      await config.update(
        "analysisMode",
        "precise",
        folder ? vscode.ConfigurationTarget.WorkspaceFolder : vscode.ConfigurationTarget.Workspace
      );
      return true;
    }
    if (!vscode.workspace.isTrusted) return false;

    const choice = await vscode.window.showInformationMessage(
      "El analisis preciso ejecuta Maven o Gradle para resolver versiones, transitivas y cadenas igual que la TUI.",
      { modal: true, detail: "La eleccion se guardara solo para este workspace." },
      "Usar analisis preciso",
      "Usar analisis rapido"
    );
    if (!choice) return undefined;
    const precise = choice === "Usar analisis preciso";
    await config.update(
      "analysisMode",
      precise ? "precise" : "fast",
      folder ? vscode.ConfigurationTarget.WorkspaceFolder : vscode.ConfigurationTarget.Workspace
    );
    return precise;
  }

  private async applySelectedUpdates(
    panel: vscode.WebviewPanel,
    projectPath: string,
    plan: UpdatePlan,
    rawIds: unknown,
    dynamic: boolean
  ): Promise<void> {
    const ids = Array.isArray(rawIds)
      ? [...new Set(rawIds.filter((id): id is string => typeof id === "string" && id.trim().length > 0))]
      : [];
    const selected = ids
      .map((id) => plan.suggestions.find((suggestion) => suggestion.id === id))
      .filter((suggestion): suggestion is UpdateSuggestion => suggestion !== undefined);

    if (ids.length === 0 || selected.length !== ids.length || selected.some((item) => !isSuggestionSafe(item))) {
      void vscode.window.showWarningMessage(
        "La seleccion contiene una actualizacion no valida. Actualiza el plan y vuelve a elegir los cambios."
      );
      return;
    }

    const choice = await vscode.window.showWarningMessage(
      `DepAnalyzer modificara ${path.basename(plan.buildFile)} con ${selected.length} ${selected.length === 1 ? "actualizacion" : "actualizaciones"}.`,
      {
        modal: true,
        detail: selected
          .map((item) => `${coordinateFor(item)}: ${item.currentVersion} -> ${item.newVersion}`)
          .join("\n")
      },
      "Aplicar seleccionadas"
    );
    if (choice !== "Aplicar seleccionadas") return;

    try {
      const buildFile = resolveBuildFile(projectPath, plan.buildFile);
      const snapshot = await this.createBuildFileSnapshot(buildFile);
      const output = await vscode.window.withProgress(
        {
          location: vscode.ProgressLocation.Notification,
          title: `DepAnalyzer: aplicando ${selected.length} actualizaciones`
        },
        () => this.cli.applyUpdates(projectPath, selected.map((item) => item.id), dynamic)
      );
      this.output.appendLine("");
      this.output.appendLine(`Actualizaciones solicitadas: ${selected.length}`);
      this.output.appendLine(output);

      panel.webview.html = buildUpdateCenterSuccessHtml(selected, createNonce());
      await vscode.commands.executeCommand(
        "vscode.diff",
        snapshot,
        buildFile,
        `DepAnalyzer: antes y despues de ${path.basename(plan.buildFile)}`
      );
      await this.analyzeProject(projectPath);
      void vscode.window.showInformationMessage(
        `DepAnalyzer proceso ${selected.length} ${selected.length === 1 ? "actualizacion" : "actualizaciones"} y volvio a analizar el proyecto.`
      );
    } catch (error) {
      const message = errorMessage(error);
      this.output.appendLine(message);
      const stale = /desactualizad|stale|no encontrad/i.test(message);
      panel.webview.html = buildUpdateCenterErrorHtml(
        stale
          ? "El plan cambio antes de aplicarse. No se modifico el proyecto; genera un plan nuevo."
          : message,
        createNonce()
      );
    }
  }

  private async enableDynamicAndReanalyze(projectPath: string): Promise<boolean> {
    const choice = await vscode.window.showInformationMessage(
      "El analisis dinamico usa Maven o Gradle para resolver las versiones reales. Puede tardar mas que el analisis normal.",
      { modal: true },
      "Activar y reanalizar"
    );
    if (choice !== "Activar y reanalizar") return false;

    const projectUri = vscode.Uri.file(projectPath);
    const folder = vscode.workspace.getWorkspaceFolder(projectUri);
    const config = vscode.workspace.getConfiguration("depanalyzer", folder?.uri);
    await config.update(
      "analysisMode",
      "precise",
      folder ? vscode.ConfigurationTarget.WorkspaceFolder : vscode.ConfigurationTarget.Workspace
    );
    await this.analyzeProject(projectPath);
    return true;
  }

  private async createBuildFileSnapshot(buildFile: vscode.Uri): Promise<vscode.Uri> {
    const snapshotDirectory = vscode.Uri.joinPath(this.context.globalStorageUri, "update-snapshots");
    await vscode.workspace.fs.createDirectory(snapshotDirectory);
    const snapshot = vscode.Uri.joinPath(
      snapshotDirectory,
      `${Date.now()}-before-${path.basename(buildFile.fsPath)}`
    );
    const contents = await vscode.workspace.fs.readFile(buildFile);
    await vscode.workspace.fs.writeFile(snapshot, contents);
    return snapshot;
  }
}

interface UpdateCenterMessage {
  command?: "apply" | "reload" | "showOutput" | "enableDynamic" | "upgradeCli";
  ids?: unknown;
}

function toUpdateCandidate(candidateOrArg?: UpdateCandidate | FindingCommandArg): UpdateCandidate | undefined {
  if (!candidateOrArg) return undefined;
  if ("finding" in candidateOrArg) {
    const finding = candidateOrArg.finding;
    if (!canSafelyUpdate(finding)) return undefined;
    return {
      projectPath: candidateOrArg.projectPath,
      groupId: finding.groupId,
      artifactId: finding.artifactId,
      currentVersion: finding.currentVersion,
      newVersion: finding.latestVersion!,
      ecosystem: finding.ecosystem
    };
  }
  return candidateOrArg;
}

function resolveBuildFile(projectPath: string, buildFile: string): vscode.Uri {
  const projectRoot = path.resolve(projectPath);
  const resolvedBuildFile = path.isAbsolute(buildFile)
    ? path.resolve(buildFile)
    : path.resolve(projectRoot, buildFile);
  const relative = path.relative(projectRoot, resolvedBuildFile);
  if (relative.startsWith(`..${path.sep}`) || relative === ".." || path.isAbsolute(relative)) {
    throw new Error("El CLI devolvio un archivo de compilacion fuera del workspace.");
  }
  return vscode.Uri.file(resolvedBuildFile);
}

function createNonce(): string {
  return randomBytes(16).toString("base64");
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

async function hasAnyFile(projectUri: vscode.Uri, names: string[]): Promise<boolean> {
  for (const name of names) {
    try {
      await vscode.workspace.fs.stat(vscode.Uri.joinPath(projectUri, name));
      return true;
    } catch {
      // Try the next supported build file.
    }
  }
  return false;
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
    return `**${finding.coordinate}** esta desactualizada\n\nVersion actual: \`${displayVersion(finding.currentVersion)}\`\n\nVersion disponible: \`${displayVersion(finding.latestVersion)}\``;
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
