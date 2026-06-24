import * as vscode from "vscode";
import type { Finding } from "./models.js";
import { summarizeFindings } from "./report-utils.js";

export class FindingsProvider implements vscode.TreeDataProvider<FindingItem> {
  private readonly changed = new vscode.EventEmitter<FindingItem | undefined>();
  readonly onDidChangeTreeData = this.changed.event;
  private findings: Finding[] = [];
  private summary = "Sin analisis ejecutado";

  refresh(findings: Finding[]): void {
    this.findings = findings;
    this.summary = findings.length === 0 ? "Sin hallazgos" : summarizeFindings(findings);
    this.changed.fire(undefined);
  }

  clear(): void {
    this.findings = [];
    this.summary = "Sin analisis ejecutado";
    this.changed.fire(undefined);
  }

  getTreeItem(item: FindingItem): vscode.TreeItem {
    return item;
  }

  getChildren(item?: FindingItem): FindingItem[] {
    if (item) return [];
    return [
      new FindingItem(this.summary, "", vscode.TreeItemCollapsibleState.None),
      ...this.findings.map((finding) => new FindingItem(
        finding.coordinate,
        formatDescription(finding),
        vscode.TreeItemCollapsibleState.None,
        finding
      ))
    ];
  }
}

export class FindingItem extends vscode.TreeItem {
  constructor(
    label: string,
    description: string,
    state: vscode.TreeItemCollapsibleState,
    finding?: Finding
  ) {
    super(label, state);
    this.description = description;
    this.tooltip = finding ? `${finding.coordinate} ${description}` : label;
    this.iconPath = finding?.kind === "vulnerability"
      ? new vscode.ThemeIcon("warning")
      : new vscode.ThemeIcon("arrow-up");
  }
}

function formatDescription(finding: Finding): string {
  if (finding.kind === "vulnerability") {
    const cve = finding.vulnerability?.cveId ?? "CVE";
    return `${finding.severity ?? "UNKNOWN"} ${cve}`;
  }
  return `${finding.currentVersion} -> ${finding.latestVersion}`;
}
