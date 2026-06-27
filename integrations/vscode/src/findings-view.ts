import * as vscode from "vscode";
import type { Finding, FindingCommandArg } from "./models.js";
import { buildFindingGroups, countFindings, type FindingGroup, type FindingGroupId } from "./findings-groups.js";
import { summarizeFindings } from "./report-utils.js";

type DepAnalyzerTreeItem = SummaryItem | GroupItem | FindingItem | StateItem;

export class FindingsProvider implements vscode.TreeDataProvider<DepAnalyzerTreeItem> {
  private readonly changed = new vscode.EventEmitter<DepAnalyzerTreeItem | undefined>();
  readonly onDidChangeTreeData = this.changed.event;
  private findings: Finding[] = [];
  private projectPath = "";
  private state: "initial" | "ready" | "error" = "initial";
  private errorMessage = "";

  refresh(findings: Finding[], projectPath: string): void {
    this.findings = findings;
    this.projectPath = projectPath;
    this.state = "ready";
    this.errorMessage = "";
    this.changed.fire(undefined);
  }

  showError(message: string): void {
    this.state = "error";
    this.errorMessage = message;
    this.changed.fire(undefined);
  }

  clear(): void {
    this.findings = [];
    this.projectPath = "";
    this.state = "initial";
    this.errorMessage = "";
    this.changed.fire(undefined);
  }

  getTreeItem(item: DepAnalyzerTreeItem): vscode.TreeItem {
    return item;
  }

  getChildren(item?: DepAnalyzerTreeItem): DepAnalyzerTreeItem[] {
    if (item instanceof GroupItem) {
      return item.group.findings.map((finding) => new FindingItem(finding, this.projectPath));
    }
    if (item) return [];
    if (this.state === "initial") {
      return [
        new StateItem(
          "Listo para analizar",
          "Ejecuta el analisis del workspace",
          new vscode.ThemeIcon("shield"),
          "depanalyzer.scanWorkspace"
        )
      ];
    }
    if (this.state === "error") {
      return [
        new StateItem(
          "No se pudo analizar",
          "Abre la salida tecnica de DepAnalyzer",
          new vscode.ThemeIcon("error"),
          "depanalyzer.showOutput",
          this.errorMessage
        )
      ];
    }
    if (this.findings.length === 0) {
      return [
        new StateItem(
          "Proyecto limpio",
          "No se detectaron vulnerabilidades ni dependencias desactualizadas",
          new vscode.ThemeIcon("pass-filled"),
          "depanalyzer.scanWorkspace"
        )
      ];
    }

    return [
      new SummaryItem(this.findings),
      ...buildFindingGroups(this.findings).map((group) => new GroupItem(group))
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

class SummaryItem extends vscode.TreeItem {
  constructor(findings: Finding[]) {
    const counts = countFindings(findings);
    super("Resumen de seguridad", vscode.TreeItemCollapsibleState.None);
    this.description = summarizeFindings(findings);
    this.tooltip = [
      `${counts.critical} criticas`,
      `${counts.high} altas`,
      `${counts.vulnerabilities} vulnerabilidades`,
      `${counts.outdated} desactualizadas`,
      `${counts.noLocation} sin ubicacion`
    ].join(" · ");
    this.iconPath = new vscode.ThemeIcon(counts.critical || counts.high ? "shield" : "shield-filled");
    this.contextValue = "depanalyzer.summary";
  }
}

class GroupItem extends vscode.TreeItem {
  constructor(readonly group: FindingGroup) {
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
    return `${finding.severity ?? "UNKNOWN"} ${cve}`;
  }
  return `${finding.currentVersion} -> ${finding.latestVersion}`;
}

function tooltipFor(finding: Finding): string {
  const lines = [`${finding.coordinate} ${formatDescription(finding)}`];
  if (finding.sourceLocation) {
    lines.push(`${finding.sourceLocation.file}:${finding.sourceLocation.line}`);
  } else {
    lines.push("Sin ubicacion exacta en archivo");
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
  if (id === "critical" || id === "high") return new vscode.ThemeIcon("flame");
  if (id === "medium" || id === "low" || id === "unknown") return new vscode.ThemeIcon("warning");
  if (id === "outdated") return new vscode.ThemeIcon("versions");
  return new vscode.ThemeIcon("question");
}

function contextFor(finding: Finding): string {
  const parts = ["depanalyzer.finding"];
  if (finding.kind === "vulnerability") parts.push("vulnerability");
  if (finding.sourceLocation) parts.push("location");
  if (finding.vulnerability?.referenceUrl) parts.push("reference");
  if (finding.latestVersion) parts.push("update");
  return parts.join(".");
}
