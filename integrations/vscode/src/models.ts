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

export type AnalysisMode = "DYNAMIC" | "STATIC" | "STATIC_FALLBACK";
export type DependencyRelationship = "direct" | "transitive" | "unknown";

export interface ProviderAnalysisMetadata {
  requested: string;
  used: string[];
  warnings: string[];
  statuses?: Record<string, string>;
}

export interface AnalysisMetadata {
  requestedMode: AnalysisMode;
  actualMode: AnalysisMode;
  projectType: string;
  ecosystems?: string[];
  durationMs: number;
  warnings: string[];
  providers: ProviderAnalysisMetadata;
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
  upToDate?: Array<DependencyBase & { version: string }>;
  outdated?: OutdatedDependency[];
  directVulnerable?: VulnerableDependency[];
  transitiveVulnerable?: VulnerableDependency[];
  vulnerabilityChains?: VulnerabilityChain[];
  dependencyTree?: DependencyTreeNode[];
  analysis?: AnalysisMetadata;
}

export interface DependencyTreeNode {
  groupId: string;
  artifactId: string;
  currentVersion: string;
  latestVersion?: string;
  isDirectDependency: boolean;
  isDependencyManagement: boolean;
  scope?: string;
  vulnerabilities: Vulnerability[];
  children: DependencyTreeNode[];
  dependencyChain?: string[];
  ecosystem?: string;
}

export interface DependencyChainNode {
  id: string;
  groupId: string;
  artifactId: string;
  version: string;
  scope?: string;
  isDependencyManagement: boolean;
  ecosystem?: string;
  coordinate?: string;
}

export interface VulnerabilityChain {
  chain: DependencyChainNode[];
  vulnerabilities: Vulnerability[];
  isShortestPath: boolean;
  classification: string;
  depth: number;
  cveIds: string[];
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
  viaDirectCoordinate?: string;
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

export interface FindingCommandArg {
  projectPath: string;
  finding: Finding;
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
  relationship?: DependencyRelationship;
  directRoot?: string;
  chainClassification?: string;
  projectPath?: string;
}

export interface CliCapabilityDocument {
  cliVersion: string;
  reportSchemas: string[];
  features: Record<string, boolean>;
}

export interface CliProgressEvent {
  stream: "depanalyzer-progress";
  type: string;
  message: string;
  timestamp: string;
  phase?: string;
  current?: number;
  total?: number;
}

export interface AnalysisRunOptions {
  dynamic: boolean;
  includeChains: boolean;
  timeoutSeconds: number;
  cancellationToken?: import("vscode").CancellationToken;
  onProgress?: (event: CliProgressEvent) => void;
}
