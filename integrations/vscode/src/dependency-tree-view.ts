import * as vscode from "vscode";
import {
  matchesDependencyTreeFilter,
  type DependencyTreeFilter
} from "./dependency-tree-utils.js";
import { displayVersion } from "./finding-presentation.js";
import type { DependencyReport, DependencyTreeNode } from "./models.js";
import { coordinate } from "./report-utils.js";

export type { DependencyTreeFilter } from "./dependency-tree-utils.js";
type TreeElement = WorkspaceTreeItem | DependencyNodeItem | TreeStateItem;

interface WorkspaceReport {
  projectPath: string;
  projectName: string;
  nodes: DependencyTreeNode[];
}

export class DependencyTreeProvider implements vscode.TreeDataProvider<TreeElement> {
  private readonly changed = new vscode.EventEmitter<TreeElement | undefined>();
  readonly onDidChangeTreeData = this.changed.event;
  private readonly reports = new Map<string, WorkspaceReport>();
  private filter: DependencyTreeFilter = "all";

  refresh(report: DependencyReport, projectPath: string): void {
    this.reports.set(projectPath, {
      projectPath,
      projectName: report.projectName,
      nodes: report.dependencyTree ?? []
    });
    this.changed.fire(undefined);
  }

  remove(projectPath: string): void {
    this.reports.delete(projectPath);
    this.changed.fire(undefined);
  }

  clear(): void {
    this.reports.clear();
    this.changed.fire(undefined);
  }

  setFilter(filter: DependencyTreeFilter): void {
    this.filter = filter;
    this.changed.fire(undefined);
  }

  getFilter(): DependencyTreeFilter {
    return this.filter;
  }

  getTreeItem(element: TreeElement): vscode.TreeItem {
    return element;
  }

  getChildren(element?: TreeElement): TreeElement[] {
    if (element instanceof WorkspaceTreeItem) {
      return this.visibleNodes(element.report.nodes)
        .map((node) => new DependencyNodeItem(node, element.report.projectPath, this.filter));
    }
    if (element instanceof DependencyNodeItem) {
      if (this.filter === "direct") return [];
      return this.visibleNodes(element.node.children)
        .map((node) => new DependencyNodeItem(node, element.projectPath, this.filter));
    }
    if (element) return [];

    const reports = [...this.reports.values()];
    if (reports.length === 0) {
      return [new TreeStateItem("Analiza el workspace para construir el arbol")];
    }
    if (reports.length === 1) {
      const report = reports[0]!;
      const nodes = this.visibleNodes(report.nodes);
      return nodes.length > 0
        ? nodes.map((node) => new DependencyNodeItem(node, report.projectPath, this.filter))
        : [new TreeStateItem(`Sin dependencias para el filtro: ${filterLabel(this.filter)}`)];
    }
    return reports.map((report) => new WorkspaceTreeItem(report, this.visibleNodes(report.nodes).length));
  }

  private visibleNodes(nodes: DependencyTreeNode[]): DependencyTreeNode[] {
    return nodes.filter((node) => matchesDependencyTreeFilter(node, this.filter));
  }
}

class WorkspaceTreeItem extends vscode.TreeItem {
  constructor(readonly report: WorkspaceReport, visibleCount: number) {
    super(report.projectName, vscode.TreeItemCollapsibleState.Expanded);
    this.description = `${visibleCount} dependencias raiz`;
    this.tooltip = report.projectPath;
    this.iconPath = new vscode.ThemeIcon("folder-library");
    this.contextValue = "depanalyzer.tree.workspace";
  }
}

class DependencyNodeItem extends vscode.TreeItem {
  constructor(
    readonly node: DependencyTreeNode,
    readonly projectPath: string,
    filter: DependencyTreeFilter
  ) {
    const hasVisibleChildren = filter !== "direct" &&
      node.children.some((child) => matchesDependencyTreeFilter(child, filter));
    super(
      coordinate(node.groupId, node.artifactId, node.ecosystem),
      hasVisibleChildren ? vscode.TreeItemCollapsibleState.Collapsed : vscode.TreeItemCollapsibleState.None
    );
    const status = [
      displayVersion(node.currentVersion),
      node.scope,
      node.isDirectDependency ? "directa" : "transitiva",
      node.vulnerabilities.length > 0 ? `${node.vulnerabilities.length} CVE` : undefined,
      node.latestVersion ? `→ ${node.latestVersion}` : undefined
    ].filter(Boolean);
    this.description = status.join(" · ");
    this.tooltip = tooltipFor(node);
    this.iconPath = iconFor(node);
    this.contextValue = node.vulnerabilities.length > 0
      ? "depanalyzer.tree.vulnerable"
      : node.latestVersion
        ? "depanalyzer.tree.outdated"
        : "depanalyzer.tree.clean";
  }
}

class TreeStateItem extends vscode.TreeItem {
  constructor(message: string) {
    super(message, vscode.TreeItemCollapsibleState.None);
    this.iconPath = new vscode.ThemeIcon("info");
  }
}

function iconFor(node: DependencyTreeNode): vscode.ThemeIcon {
  const severities = node.vulnerabilities.map((item) => item.severity);
  if (severities.includes("CRITICAL") || severities.includes("HIGH")) return new vscode.ThemeIcon("error");
  if (severities.includes("MEDIUM")) return new vscode.ThemeIcon("warning");
  if (severities.length > 0) return new vscode.ThemeIcon("shield");
  if (node.latestVersion) return new vscode.ThemeIcon("arrow-up");
  return new vscode.ThemeIcon(node.isDirectDependency ? "package" : "symbol-property");
}

function tooltipFor(node: DependencyTreeNode): string {
  const lines = [
    `${coordinate(node.groupId, node.artifactId, node.ecosystem)}:${displayVersion(node.currentVersion)}`,
    node.isDirectDependency ? "Dependencia directa" : "Dependencia transitiva",
    node.scope ? `Scope: ${node.scope}` : undefined,
    node.latestVersion ? `Disponible: ${node.latestVersion}` : undefined,
    ...node.vulnerabilities.map((item) => `${item.severity} ${item.cveId}`)
  ];
  return lines.filter(Boolean).join("\n");
}

function filterLabel(filter: DependencyTreeFilter): string {
  switch (filter) {
    case "all": return "todas";
    case "problems": return "problematicas";
    case "vulnerable": return "vulnerables";
    case "direct": return "directas";
  }
}
