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
  assert.equal(summarizeFindings(findings), "1 criticas, 0 altas, 1 vulnerabilidades, 1 desactualizadas");
});
