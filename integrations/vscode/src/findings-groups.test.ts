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
  sourceLocation: { file: "build.gradle", line: 10, startColumn: 3, endColumn: 12 }
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
  vulnerability: { cveId: "CVE-2026-0002", severity: "HIGH" }
};

test("groups located findings by risk before outdated dependencies", () => {
  const groups = buildFindingGroups([locatedOutdated, locatedCritical]);

  assert.deepEqual(groups.map((group) => group.id), ["critical", "outdated"]);
  assert.equal(groups[0]?.label, "Vulnerabilidades criticas");
  assert.equal(groups[0]?.findings[0]?.coordinate, "org.example:critical");
});

test("keeps findings without source location in a dedicated group", () => {
  const groups = buildFindingGroups([noLocationHigh, locatedOutdated]);

  assert.deepEqual(groups.map((group) => group.id), ["outdated", "noLocation"]);
  assert.equal(groups[1]?.label, "Sin ubicacion detectada");
  assert.equal(groups[1]?.findings[0]?.severity, "HIGH");
});

test("counts summary values for visual state", () => {
  const counts = countFindings([locatedCritical, locatedOutdated, noLocationHigh]);

  assert.deepEqual(counts, {
    critical: 1,
    high: 1,
    vulnerabilities: 2,
    outdated: 1,
    noLocation: 1
  });
});
