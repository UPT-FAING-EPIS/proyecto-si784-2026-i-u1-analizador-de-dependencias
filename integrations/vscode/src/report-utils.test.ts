import assert from "node:assert/strict";
import test from "node:test";
import { coordinate, flattenFindings, isSupportedDependencyFile, summarizeFindings } from "./report-utils.js";

test("detects supported dependency files", () => {
  assert.equal(isSupportedDependencyFile("pom.xml"), true);
  assert.equal(isSupportedDependencyFile("src/package.json"), true);
  assert.equal(isSupportedDependencyFile("README.md"), false);
});

test("formats ecosystem coordinates", () => {
  assert.equal(coordinate("org.example", "demo", "MAVEN"), "org.example:demo");
  assert.equal(coordinate("npm", "lodash", "NPM"), "lodash");
  assert.equal(coordinate("@types", "node", "NPM"), "@types/node");
  assert.equal(coordinate("pypi", "requests", "PYPI"), "requests");
});

test("flattens and summarizes report findings", () => {
  const findings = flattenFindings({
    schemaVersion: "1.0",
    projectName: "demo",
    directVulnerable: [{
      groupId: "org.example",
      artifactId: "demo",
      version: "1.0.0",
      vulnerabilities: [{
        cveId: "CVE-2026-0001",
        severity: "CRITICAL"
      }]
    }],
    outdated: [{
      groupId: "org.example",
      artifactId: "demo",
      currentVersion: "1.0.0",
      latestVersion: "1.1.0"
    }]
  });

  assert.equal(findings.length, 2);
  assert.equal(findings[0]?.severity, "CRITICAL");
  assert.equal(findings[0]?.latestVersion, "1.1.0");
  assert.equal(findings[0]?.relationship, "direct");
  assert.equal(summarizeFindings(findings), "1 criticas, 0 altas, 1 dependencias vulnerables, 1 CVE, 1 desactualizadas");
});

test("joins a transitive CVE with its complete dependency chain", () => {
  const findings = flattenFindings({
    schemaVersion: "1.1",
    projectName: "demo",
    transitiveVulnerable: [{
      groupId: "org.example",
      artifactId: "transitive",
      version: "2.0.0",
      vulnerabilities: [{ cveId: "CVE-2026-0002", severity: "HIGH" }]
    }],
    vulnerabilityChains: [{
      chain: [
        {
          id: "root",
          groupId: "org.example",
          artifactId: "root",
          version: "1.0.0",
          isDependencyManagement: false
        },
        {
          id: "transitive",
          groupId: "org.example",
          artifactId: "transitive",
          version: "2.0.0",
          isDependencyManagement: false
        }
      ],
      vulnerabilities: [{ cveId: "CVE-2026-0002", severity: "HIGH" }],
      isShortestPath: true,
      classification: "RUNTIME",
      depth: 1,
      cveIds: ["CVE-2026-0002"]
    }]
  }, "C:\\demo");

  assert.equal(findings[0]?.relationship, "transitive");
  assert.equal(findings[0]?.directRoot, "org.example:root:1.0.0");
  assert.equal(findings[0]?.chainClassification, "RUNTIME");
  assert.deepEqual(findings[0]?.dependencyChain, [
    "org.example:root:1.0.0",
    "org.example:transitive:2.0.0"
  ]);
  assert.equal(findings[0]?.projectPath, "C:\\demo");
});
