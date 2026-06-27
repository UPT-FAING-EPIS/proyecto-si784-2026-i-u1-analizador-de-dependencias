import type { Finding } from "./models.js";
import { compareFindings } from "./report-utils.js";

export type FindingGroupId =
  | "critical"
  | "high"
  | "medium"
  | "low"
  | "unknown"
  | "outdated"
  | "noLocation";

export interface FindingGroup {
  id: FindingGroupId;
  label: string;
  description: string;
  findings: Finding[];
}

const vulnerabilityGroups: Array<{
  id: FindingGroupId;
  severity: Finding["severity"];
  label: string;
}> = [
  { id: "critical", severity: "CRITICAL", label: "Vulnerabilidades criticas" },
  { id: "high", severity: "HIGH", label: "Vulnerabilidades altas" },
  { id: "medium", severity: "MEDIUM", label: "Vulnerabilidades medias" },
  { id: "low", severity: "LOW", label: "Vulnerabilidades bajas" },
  { id: "unknown", severity: "UNKNOWN", label: "Vulnerabilidades sin severidad" }
];

export function buildFindingGroups(findings: Finding[]): FindingGroup[] {
  const located = findings.filter((finding) => finding.sourceLocation);
  const noLocation = findings.filter((finding) => !finding.sourceLocation);
  const groups: FindingGroup[] = [];

  for (const group of vulnerabilityGroups) {
    const items = located
      .filter((finding) => finding.kind === "vulnerability" && finding.severity === group.severity)
      .sort(compareFindings);
    if (items.length > 0) groups.push(toGroup(group.id, group.label, items));
  }

  const outdated = located
    .filter((finding) => finding.kind === "outdated")
    .sort(compareFindings);
  if (outdated.length > 0) groups.push(toGroup("outdated", "Dependencias desactualizadas", outdated));

  if (noLocation.length > 0) {
    groups.push(toGroup("noLocation", "Sin ubicacion detectada", noLocation.sort(compareFindings)));
  }

  return groups;
}

export function countFindings(findings: Finding[]): {
  critical: number;
  high: number;
  vulnerabilities: number;
  outdated: number;
  noLocation: number;
} {
  return {
    critical: findings.filter((finding) => finding.severity === "CRITICAL").length,
    high: findings.filter((finding) => finding.severity === "HIGH").length,
    vulnerabilities: findings.filter((finding) => finding.kind === "vulnerability").length,
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
