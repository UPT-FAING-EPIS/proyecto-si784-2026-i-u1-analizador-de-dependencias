import assert from "node:assert/strict";
import test from "node:test";
import {
  capabilitiesFromDocument,
  detectCliCapabilities,
  parseCapabilityDocument
} from "./cli-capabilities.js";

test("detects the current stdout and update-plan CLI interface", () => {
  const capabilities = detectCliCapabilities(
    "--output-file <path>\n--quiet\n--progress-json\n--tree-expand\n--show-chains",
    "--plan\n--output-file <path>\n--apply-id <id>"
  );

  assert.deepEqual(capabilities, {
    reportSchemas: ["1.0"],
    analyzeStdout: true,
    progressJson: true,
    dependencyTree: true,
    vulnerabilityChains: true,
    updatePlan: true,
    applyById: true,
    modernContract: false
  });
});

test("falls back for the public legacy CLI interface", () => {
  const capabilities = detectCliCapabilities(
    "--output=<text>\n--timeout=<int>",
    "--dry-run\n--only-security"
  );

  assert.deepEqual(capabilities, {
    reportSchemas: ["1.0"],
    analyzeStdout: false,
    progressJson: false,
    dependencyTree: false,
    vulnerabilityChains: false,
    updatePlan: false,
    applyById: false,
    modernContract: false
  });
});

test("uses the structured CLI capabilities contract", () => {
  const document = parseCapabilityDocument(JSON.stringify({
    cliVersion: "2.2.0",
    reportSchemas: ["1.0", "1.1"],
    features: {
      "analyze.outputFile": true,
      "analyze.progressJson": true,
      "report.dependencyTree": true,
      "report.vulnerabilityChains": true,
      "update.plan": true,
      "update.applyById": true
    }
  }));

  assert.deepEqual(capabilitiesFromDocument(document), {
    cliVersion: "2.2.0",
    reportSchemas: ["1.0", "1.1"],
    analyzeStdout: true,
    progressJson: true,
    dependencyTree: true,
    vulnerabilityChains: true,
    updatePlan: true,
    applyById: true,
    modernContract: true
  });
});
