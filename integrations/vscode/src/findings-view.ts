import * as vscode from "vscode";
import type { CliCapabilities } from "./cli-capabilities.js";
import { canSafelyUpdate, displayVersion } from "./finding-presentation.js";
import { buildFindingGroups, countFindings, type FindingGroup, type FindingGroupId } from "./findings-groups.js";
import type { DependencyReport, Finding, FindingCommandArg } from "./models.js";
import { summarizeFindings } from "./report-utils.js";

type DepAnalyzerTreeItem =
  | ProjectItem
  | AnalysisStatusItem
  | SummaryItem
  | GroupItem
  | FindingItem
  | StateItem;

interface ProjectFindings {
  projectPath: string;
  projectName: string;
  findings: Finding[];
  report: DependencyReport;
  capabilities: CliCapabilities;
  analyzedAt: Date;
  error?: string;
}

export class FindingsProvider implements vscode.TreeDataProvider<DepAnalyzerTreeItem> {
  private readonly changed = new vscode.EventEmitter<DepAnalyzerTreeItem | undefined>();
  readonly onDidChangeTreeData = this.changed.event;
  private readonly projects = new Map<string, ProjectFindings>();

  refresh(
    findings: Finding[],
    projectPath: string,
    report: DependencyReport,
    capabilities: CliCapabilities
  ): void {
    this.projects.set(projectPath, {
      projectPath,
      projectName: report.projectName,
      findings,
      report,
      capabilities,
      analyzedAt: new Date()
    });
    this.changed.fire(undefined);
  }

  showError(message: string, projectPath = ""): void {
    const current = this.projects.get(projectPath);
    if (current) {
      current.error = message;
    } else {
      this.projects.set(projectPath, {
        projectPath,
        projectName: projectPath ? projectPath.split(/[\\/]/).pop() ?? projectPath : "Workspace",
        findings: [],
        report: { schemaVersion: "unknown", projectName: "Workspace" },
        capabilities: legacyCapabilities(),
        analyzedAt: new Date(),
        error: message
      });
    }
    this.changed.fire(undefined);
  }

  clear(projectPath?: string): void {
    if (projectPath) this.projects.delete(projectPath);
    else this.projects.clear();
    this.changed.fire(undefined);
  }

  getTreeItem(item: DepAnalyzerTreeItem): vscode.TreeItem {
    return item;
  }

  getChildren(item?: DepAnalyzerTreeItem): DepAnalyzerTreeItem[] {
    if (item instanceof ProjectItem) return this.projectChildren(item.project);
    if (item instanceof GroupItem) {
      return item.group.findings.map((finding) => new FindingItem(finding, item.projectPath));
    }
    if (item) return [];

    const projects = [...this.projects.values()];
    if (projects.length === 0) {
      return [
        new StateItem(
          "Listo para analizar",
          "Ejecuta el analisis del workspace",
          new vscode.ThemeIcon("shield"),
          "depanalyzer.scanWorkspace"
        )
      ];
    }
    if (projects.length === 1) return this.projectChildren(projects[0]!);
    return projects.map((project) => new ProjectItem(project));
  }

  private projectChildren(project: ProjectFindings): DepAnalyzerTreeItem[] {
    if (project.error) {
      return [
        new StateItem(
          "No se pudo analizar",
          "Abre la salida tecnica de DepAnalyzer",
          new vscode.ThemeIcon("error"),
          "depanalyzer.showOutput",
          project.error
        )
      ];
    }
    const status = new AnalysisStatusItem(project);
    if (project.findings.length === 0) {
      return [
        status,
        new StateItem(
          "Proyecto limpio",
          "No se detectaron vulnerabilidades ni dependencias desactualizadas",
          new vscode.ThemeIcon("pass-filled"),
          "depanalyzer.scanWorkspace"
        )
      ];
    }
    return [
      status,
      new SummaryItem(project.findings),
      ...buildFindingGroups(project.findings)
        .map((group) => new GroupItem(group, project.projectPath))
    ];
  }
}

export class FindingItem extends vscode.TreeItem {
  readonly arg: FindingCommandArg;

  constructor(readonly finding: Finding, projectPath: string) {
    super(finding.coordinate, vscode.TreeItemCollapsibleState.None);
    this.arg = { finding, projectPath };
    this.description = formatDescription(finding);
    this.tooltip = tooltipFor(finding);
    this.iconPath = iconForFinding(finding);
    this.contextValue = contextFor(finding);
    const command = finding.kind === "vulnerability" || !finding.sourceLocation
      ? "depanalyzer.showFindingDetails"
      : "depanalyzer.openFindingLocation";
    this.command = {
      command,
      title: command === "depanalyzer.showFindingDetails" ? "Ver detalle" : "Abrir archivo",
      arguments: [this.arg]
    };
  }
}

class ProjectItem extends vscode.TreeItem {
  constructor(readonly project: ProjectFindings) {
    super(project.projectName, vscode.TreeItemCollapsibleState.Expanded);
    this.description = summarizeFindings(project.findings);
    this.tooltip = project.projectPath;
    this.iconPath = new vscode.ThemeIcon("folder-library");
  }
}

class AnalysisStatusItem extends vscode.TreeItem {
  constructor(project: ProjectFindings) {
    const analysis = project.report.analysis;
    const limited = !project.capabilities.modernContract;
    const fallback = analysis?.actualMode === "STATIC_FALLBACK";
    const label = limited
      ? "Analisis limitado: CLI antiguo"
      : fallback
        ? "Analisis degradado"
        : analysis?.actualMode === "DYNAMIC"
          ? "Analisis preciso"
          : "Analisis rapido";
    super(label, vscode.TreeItemCollapsibleState.None);
    const providers = analysis?.providers.used.join(" + ") || "sin confirmar";
    const duration = analysis ? `${(analysis.durationMs / 1000).toFixed(1)}s` : "duracion no disponible";
    this.description = `${providers} · ${duration} · ${project.analyzedAt.toLocaleTimeString()}`;
    this.tooltip = [
      `CLI: ${project.capabilities.cliVersion ?? "version antigua"}`,
      `Schema: ${project.report.schemaVersion}`,
      `Proyecto: ${analysis?.projectType ?? "no reportado"}`,
      `Ecosistemas: ${analysis?.ecosystems?.join(", ") || "no reportados"}`,
      `Modo solicitado: ${analysis?.requestedMode ?? "no reportado"}`,
      `Modo usado: ${analysis?.actualMode ?? "no reportado"}`,
      ...Object.entries(analysis?.providers.statuses ?? {})
        .map(([provider, status]) => `${provider}: ${status}`),
      ...analysis?.warnings ?? [],
      ...analysis?.providers.warnings ?? []
    ].join("\n");
    this.iconPath = new vscode.ThemeIcon(limited || fallback ? "warning" : "pass-filled");
    this.contextValue = limited ? "depanalyzer.analysis.limited" : "depanalyzer.analysis.complete";
    if (limited) {
      this.command = { command: "depanalyzer.guideCliUpgrade", title: "Actualizar CLI" };
    } else if (fallback) {
      this.command = { command: "depanalyzer.showOutput", title: "Ver salida tecnica" };
    }
  }
}

class SummaryItem extends vscode.TreeItem {
  constructor(findings: Finding[]) {
    const counts = countFindings(findings);
    super("Resumen de seguridad", vscode.TreeItemCollapsibleState.None);
    this.description = summarizeFindings(findings);
    this.tooltip = [
      `${counts.critical} criticas`,
      `${counts.high} altas`,
      `${counts.vulnerableDependencies} dependencias vulnerables`,
      `${counts.vulnerabilities} CVE`,
      `${counts.outdated} desactualizadas`
    ].join(" · ");
    this.iconPath = new vscode.ThemeIcon(counts.critical || counts.high ? "shield" : "shield-filled");
    this.contextValue = "depanalyzer.summary";
  }
}

class GroupItem extends vscode.TreeItem {
  constructor(readonly group: FindingGroup, readonly projectPath: string) {
    super(group.label, vscode.TreeItemCollapsibleState.Expanded);
    this.description = group.description;
    this.tooltip = `${group.label}: ${group.description}`;
    this.iconPath = iconForGroup(group.id);
    this.contextValue = `depanalyzer.group.${group.id}`;
  }
}

class StateItem extends vscode.TreeItem {
  constructor(label: string, description: string, icon: vscode.ThemeIcon, command: string, tooltip?: string) {
    super(label, vscode.TreeItemCollapsibleState.None);
    this.description = description;
    this.tooltip = tooltip ?? `${label}: ${description}`;
    this.iconPath = icon;
    this.contextValue = "depanalyzer.state";
    this.command = { command, title: label };
  }
}

function formatDescription(finding: Finding): string {
  if (finding.kind === "vulnerability") {
    const cve = finding.vulnerability?.cveId ?? "CVE";
    return `${severityLabel(finding.severity)} · ${cve} · ${relationshipLabel(finding.relationship)}`;
  }
  return `${displayVersion(finding.currentVersion)} -> ${displayVersion(finding.latestVersion)}`;
}

function tooltipFor(finding: Finding): string {
  const lines = [`${finding.coordinate} ${formatDescription(finding)}`];
  if (finding.sourceLocation) {
    lines.push(`${finding.sourceLocation.file}:${finding.sourceLocation.line}`);
  } else {
    lines.push("Sin ubicacion exacta en archivo");
  }
  if (finding.directRoot) lines.push(`Raiz directa: ${finding.directRoot}`);
  if (finding.dependencyChain?.length) lines.push(`Cadena: ${finding.dependencyChain.join(" -> ")}`);
  if (finding.kind === "outdated" && !canSafelyUpdate(finding)) {
    lines.push("Usa analisis preciso para verificar la version actual");
  }
  return lines.join("\n");
}

function iconForFinding(finding: Finding): vscode.ThemeIcon {
  if (finding.kind === "outdated") return new vscode.ThemeIcon("arrow-up");
  if (finding.severity === "CRITICAL" || finding.severity === "HIGH") return new vscode.ThemeIcon("error");
  if (finding.severity === "MEDIUM") return new vscode.ThemeIcon("warning");
  return new vscode.ThemeIcon("info");
}

function iconForGroup(id: FindingGroupId): vscode.ThemeIcon {
  if (id === "direct") return new vscode.ThemeIcon("shield");
  if (id === "transitive") return new vscode.ThemeIcon("type-hierarchy-sub");
  if (id === "outdated") return new vscode.ThemeIcon("versions");
  return new vscode.ThemeIcon("question");
}

function contextFor(finding: Finding): string {
  const parts = ["depanalyzer.finding"];
  if (finding.kind === "vulnerability") parts.push("vulnerability");
  if (finding.sourceLocation) parts.push("location");
  if (finding.vulnerability?.referenceUrl) parts.push("reference");
  if (canSafelyUpdate(finding)) parts.push("update");
  return parts.join(".");
}

function severityLabel(severity?: Finding["severity"]): string {
  switch (severity) {
    case "CRITICAL": return "CRITICA";
    case "HIGH": return "ALTA";
    case "MEDIUM": return "MEDIA";
    case "LOW": return "BAJA";
    case "UNKNOWN":
    default:
      return "SIN CLASIFICAR";
  }
}

function relationshipLabel(relationship?: Finding["relationship"]): string {
  if (relationship === "direct") return "directa";
  if (relationship === "transitive") return "transitiva";
  return "relacion no determinada";
}

function legacyCapabilities(): CliCapabilities {
  return {
    reportSchemas: ["1.0"],
    analyzeStdout: false,
    progressJson: false,
    dependencyTree: false,
    vulnerabilityChains: false,
    updatePlan: false,
    applyById: false,
    modernContract: false
  };
}
