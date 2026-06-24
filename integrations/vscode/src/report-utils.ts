import type { DependencyReport, Finding, OutdatedDependency, VulnerableDependency } from "./models.js";

const SUPPORTED_FILES = new Set([
  "pom.xml",
  "build.gradle",
  "build.gradle.kts",
  "package.json",
  "pyproject.toml",
  "requirements.txt"
]);

const severityWeight = {
  CRITICAL: 5,
  HIGH: 4,
  MEDIUM: 3,
  LOW: 2,
  UNKNOWN: 1
} as const;

export function isSupportedDependencyFile(fileName: string): boolean {
  return SUPPORTED_FILES.has(fileName.replace(/\\/g, "/").split("/").pop() ?? "");
}

export function coordinate(groupId: string, artifactId: string, ecosystem = "MAVEN"): string {
  if (ecosystem === "NPM") {
    return groupId === "npm" ? artifactId : `${groupId}/${artifactId}`;
  }
  if (ecosystem === "PYPI") {
    return artifactId;
  }
  return `${groupId}:${artifactId}`;
}

export function flattenFindings(report: DependencyReport): Finding[] {
  const outdatedByKey = new Map<string, OutdatedDependency>();
  for (const dep of report.outdated ?? []) {
    outdatedByKey.set(keyFor(dep.groupId, dep.artifactId, dep.ecosystem), dep);
  }

  const findings: Finding[] = [];
  for (const dep of report.directVulnerable ?? []) {
    for (const vulnerability of dep.vulnerabilities ?? []) {
      const update = outdatedByKey.get(keyFor(dep.groupId, dep.artifactId, dep.ecosystem));
      findings.push({
        kind: "vulnerability",
        groupId: dep.groupId,
        artifactId: dep.artifactId,
        coordinate: coordinate(dep.groupId, dep.artifactId, dep.ecosystem),
        currentVersion: dep.version,
        latestVersion: update?.latestVersion,
        ecosystem: dep.ecosystem,
        severity: vulnerability.severity,
        vulnerability,
        sourceLocation: dep.sourceLocation,
        dependencyChain: dep.dependencyChain
      });
    }
  }

  for (const dep of report.transitiveVulnerable ?? []) {
    for (const vulnerability of dep.vulnerabilities ?? []) {
      findings.push({
        kind: "vulnerability",
        groupId: dep.groupId,
        artifactId: dep.artifactId,
        coordinate: coordinate(dep.groupId, dep.artifactId, dep.ecosystem),
        currentVersion: dep.version,
        ecosystem: dep.ecosystem,
        severity: vulnerability.severity,
        vulnerability,
        dependencyChain: dep.dependencyChain
      });
    }
  }

  for (const dep of report.outdated ?? []) {
    findings.push({
      kind: "outdated",
      groupId: dep.groupId,
      artifactId: dep.artifactId,
      coordinate: coordinate(dep.groupId, dep.artifactId, dep.ecosystem),
      currentVersion: dep.currentVersion,
      latestVersion: dep.latestVersion,
      ecosystem: dep.ecosystem,
      sourceLocation: dep.sourceLocation
    });
  }

  return findings.sort(compareFindings);
}

export function summarizeFindings(findings: Finding[]): string {
  const critical = findings.filter((finding) => finding.severity === "CRITICAL").length;
  const high = findings.filter((finding) => finding.severity === "HIGH").length;
  const vulnerable = findings.filter((finding) => finding.kind === "vulnerability").length;
  const outdated = findings.filter((finding) => finding.kind === "outdated").length;
  return `${critical} criticas, ${high} altas, ${vulnerable} vulnerabilidades, ${outdated} desactualizadas`;
}

export function compareFindings(left: Finding, right: Finding): number {
  const leftWeight = left.severity ? severityWeight[left.severity] : 0;
  const rightWeight = right.severity ? severityWeight[right.severity] : 0;
  return rightWeight - leftWeight || left.coordinate.localeCompare(right.coordinate);
}

export function keyFor(groupId: string, artifactId: string, ecosystem = "MAVEN"): string {
  return `${ecosystem}:${groupId}:${artifactId}`;
}

export function keyForVulnerable(dep: VulnerableDependency): string {
  return keyFor(dep.groupId, dep.artifactId, dep.ecosystem);
}
