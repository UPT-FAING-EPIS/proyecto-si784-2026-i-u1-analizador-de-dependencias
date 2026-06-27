import type { Finding } from "./models.js";
import { isVersionResolved } from "./finding-presentation.js";
import { compareFindings } from "./report-utils.js";

export type FindingGroupId =
  | "direct"
  | "transitive"
  | "outdated"
  | "unresolved";

export interface FindingGroup {
  id: FindingGroupId;
  label: string;
  description: string;
  findings: Finding[];
}

export function buildFindingGroups(findings: Finding[]): FindingGroup[] {
  const groups: FindingGroup[] = [];

  const direct = findings
    .filter((finding) => finding.kind === "vulnerability" && finding.relationship === "direct")
    .sort(compareFindings);
  if (direct.length > 0) groups.push(toGroup("direct", "Vulnerabilidades directas", direct));

  const transitive = findings
    .filter((finding) => finding.kind === "vulnerability" && finding.relationship === "transitive")
    .sort(compareFindings);
  if (transitive.length > 0) groups.push(toGroup("transitive", "Vulnerabilidades transitivas", transitive));

  const outdated = findings
    .filter((finding) => finding.kind === "outdated" && isVersionResolved(finding.currentVersion))
    .sort(compareFindings);
  if (outdated.length > 0) groups.push(toGroup("outdated", "Dependencias desactualizadas", outdated));

  const unresolved = findings
    .filter((finding) => finding.kind === "outdated" && !isVersionResolved(finding.currentVersion))
    .sort(compareFindings);
  if (unresolved.length > 0) groups.push(toGroup("unresolved", "Versiones no resueltas", unresolved));

  return groups;
}

export function countFindings(findings: Finding[]): {
  critical: number;
  high: number;
  vulnerabilities: number;
  vulnerableDependencies: number;
  outdated: number;
  noLocation: number;
} {
  const vulnerableFindings = findings.filter((finding) => finding.kind === "vulnerability");
  return {
    critical: findings.filter((finding) => finding.severity === "CRITICAL").length,
    high: findings.filter((finding) => finding.severity === "HIGH").length,
    vulnerabilities: vulnerableFindings.length,
    vulnerableDependencies: new Set(vulnerableFindings.map((finding) => finding.coordinate)).size,
    outdated: findings.filter((finding) => finding.kind === "outdated").length,
    noLocation: findings.filter((finding) => !finding.sourceLocation).length
  };
}

function toGroup(id: FindingGroupId, label: string, findings: Finding[]): FindingGroup {
  return {
    id,
    label,
    description: `${findings.length} ${findings.length === 1 ? "hallazgo" : "hallazgos"}`,
    findings
  };
}
