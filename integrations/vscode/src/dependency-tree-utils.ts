import type { DependencyTreeNode } from "./models.js";

export type DependencyTreeFilter = "all" | "problems" | "vulnerable" | "direct";

export function matchesDependencyTreeFilter(
  node: DependencyTreeNode,
  filter: DependencyTreeFilter
): boolean {
  if (filter === "all") return true;
  if (filter === "direct") return node.isDirectDependency;

  const ownMatch = filter === "vulnerable"
    ? node.vulnerabilities.length > 0
    : node.vulnerabilities.length > 0 || Boolean(node.latestVersion);
  return ownMatch || node.children.some((child) => matchesDependencyTreeFilter(child, filter));
}
