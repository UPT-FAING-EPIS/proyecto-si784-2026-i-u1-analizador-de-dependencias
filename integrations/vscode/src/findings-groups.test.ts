import assert from "node:assert/strict";
import test from "node:test";
import { buildFindingGroups, countFindings } from "./findings-groups.js";
import type { Finding } from "./models.js";

const locatedCritical: Finding = {
  kind: "vulnerability",
  groupId: "org.example",
  artifactId: "critical",
  coordinate: "org.example:critical",
  currentVersion: "1.0.0",
  severity: "CRITICAL",
  vulnerability: { cveId: "CVE-2026-0001", severity: "CRITICAL" },
  sourceLocation: { file: "build.gradle", line: 10, startColumn: 3, endColumn: 12 },
  relationship: "direct"
};

const locatedOutdated: Finding = {
  kind: "outdated",
  groupId: "org.example",
  artifactId: "outdated",
  coordinate: "org.example:outdated",
  currentVersion: "1.0.0",
  latestVersion: "1.1.0",
  sourceLocation: { file: "build.gradle", line: 12, startColumn: 3, endColumn: 12 }
};

const noLocationHigh: Finding = {
  kind: "vulnerability",
  groupId: "org.example",
  artifactId: "noloc",
  coordinate: "org.example:noloc",
  currentVersion: "1.0.0",
  severity: "HIGH",
  vulnerability: { cveId: "CVE-2026-0002", severity: "HIGH" },
  relationship: "transitive"
};

test("groups direct vulnerabilities before outdated dependencies", () => {
  const groups = buildFindingGroups([locatedOutdated, locatedCritical]);

  assert.deepEqual(groups.map((group) => group.id), ["direct", "outdated"]);
  assert.equal(groups[0]?.label, "Vulnerabilidades directas");
  assert.equal(groups[0]?.findings[0]?.coordinate, "org.example:critical");
});

test("keeps transitive vulnerabilities separate from ordinary updates", () => {
  const groups = buildFindingGroups([noLocationHigh, locatedOutdated]);

  assert.deepEqual(groups.map((group) => group.id), ["transitive", "outdated"]);
  assert.equal(groups[0]?.label, "Vulnerabilidades transitivas");
  assert.equal(groups[0]?.findings[0]?.severity, "HIGH");
});

test("counts summary values for visual state", () => {
  const counts = countFindings([locatedCritical, locatedOutdated, noLocationHigh]);

  assert.deepEqual(counts, {
    critical: 1,
    high: 1,
    vulnerabilities: 2,
    vulnerableDependencies: 2,
    outdated: 1,
    noLocation: 1
  });
});
