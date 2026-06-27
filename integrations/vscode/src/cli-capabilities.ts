import type { CliCapabilityDocument } from "./models.js";

export interface CliCapabilities {
  cliVersion?: string;
  reportSchemas: string[];
  analyzeStdout: boolean;
  progressJson: boolean;
  dependencyTree: boolean;
  vulnerabilityChains: boolean;
  updatePlan: boolean;
  applyById: boolean;
  modernContract: boolean;
}

export function detectCliCapabilities(analyzeHelp: string, updateHelp: string): CliCapabilities {
  return {
    reportSchemas: ["1.0"],
    analyzeStdout: analyzeHelp.includes("--output-file") && analyzeHelp.includes("--quiet"),
    progressJson: analyzeHelp.includes("--progress-json"),
    dependencyTree: analyzeHelp.includes("--tree-expand"),
    vulnerabilityChains: analyzeHelp.includes("--show-chains"),
    updatePlan: updateHelp.includes("--plan") && updateHelp.includes("--output-file"),
    applyById: updateHelp.includes("--apply-id"),
    modernContract: false
  };
}

export function capabilitiesFromDocument(document: CliCapabilityDocument): CliCapabilities {
  const feature = (name: string): boolean => document.features[name] === true;
  return {
    cliVersion: document.cliVersion,
    reportSchemas: document.reportSchemas,
    analyzeStdout: feature("analyze.outputFile") || feature("analyze.stdout"),
    progressJson: feature("analyze.progressJson"),
    dependencyTree: feature("report.dependencyTree"),
    vulnerabilityChains: feature("report.vulnerabilityChains"),
    updatePlan: feature("update.plan"),
    applyById: feature("update.applyById"),
    modernContract: true
  };
}

export function parseCapabilityDocument(raw: string): CliCapabilityDocument {
  const value = JSON.parse(raw) as Partial<CliCapabilityDocument>;
  if (
    typeof value.cliVersion !== "string" ||
    !Array.isArray(value.reportSchemas) ||
    value.reportSchemas.some((item) => typeof item !== "string") ||
    !value.features ||
    typeof value.features !== "object"
  ) {
    throw new Error("Documento de capacidades invalido");
  }
  return value as CliCapabilityDocument;
}
