export type ProviderMode = "auto" | "oss" | "nvd";

export interface SourceLocation {
  file: string;
  line: number;
  startColumn: number;
  endColumn: number;
}

export interface Vulnerability {
  cveId: string;
  severity: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "UNKNOWN";
  cvssScore?: number;
  description?: string;
  source?: string;
  referenceUrl?: string;
}

export interface DependencyBase {
  groupId: string;
  artifactId: string;
  ecosystem?: string;
  sourceLocation?: SourceLocation;
}

export interface OutdatedDependency extends DependencyBase {
  currentVersion: string;
  latestVersion: string;
}

export interface VulnerableDependency extends DependencyBase {
  version: string;
  vulnerabilities: Vulnerability[];
  dependencyChain?: string[];
}

export interface DependencyReport {
  schemaVersion: string;
  projectName: string;
  outdated?: OutdatedDependency[];
  directVulnerable?: VulnerableDependency[];
  transitiveVulnerable?: VulnerableDependency[];
}

export interface UpdateSuggestion {
  id: string;
  groupId: string;
  artifactId: string;
  currentVersion: string;
  newVersion: string;
  reason: string;
  targetType: string;
  ecosystem: string;
}

export interface UpdatePlan {
  schemaVersion: string;
  projectType: string;
  buildFile: string;
  suggestions: UpdateSuggestion[];
}

export interface UpdateCandidate {
  projectPath: string;
  groupId: string;
  artifactId: string;
  currentVersion: string;
  newVersion: string;
  ecosystem?: string;
}

export interface Finding {
  kind: "vulnerability" | "outdated";
  groupId: string;
  artifactId: string;
  coordinate: string;
  currentVersion: string;
  latestVersion?: string;
  ecosystem?: string;
  severity?: Vulnerability["severity"];
  vulnerability?: Vulnerability;
  sourceLocation?: SourceLocation;
  dependencyChain?: string[];
}
