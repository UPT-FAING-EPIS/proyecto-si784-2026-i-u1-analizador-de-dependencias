import assert from "node:assert/strict";
import test from "node:test";
import {
  buildFindingNarrative,
  canSafelyUpdate,
  displayVersion,
  versionChangeKind
} from "./finding-presentation.js";
import type { Finding } from "./models.js";

const outdated: Finding = {
  kind: "outdated",
  groupId: "org.example",
  artifactId: "demo",
  coordinate: "org.example:demo",
  currentVersion: "1.2.3",
  latestVersion: "2.0.0"
};

test("presents unresolved versions without exposing unknown", () => {
  assert.equal(displayVersion("unknown"), "Version no detectada");
  assert.equal(displayVersion("  N/A  "), "Version no detectada");
  assert.equal(displayVersion("1.2.3"), "1.2.3");
});

test("only enables updates with two resolved versions", () => {
  assert.equal(canSafelyUpdate(outdated), true);
  assert.equal(canSafelyUpdate({ ...outdated, currentVersion: "unknown" }), false);
  assert.equal(canSafelyUpdate({ ...outdated, latestVersion: undefined }), false);
});

test("classifies semantic version changes", () => {
  assert.equal(versionChangeKind("1.2.3", "2.0.0"), "major");
  assert.equal(versionChangeKind("1.2.3", "1.4.0"), "minor");
  assert.equal(versionChangeKind("1.2.3", "1.2.8"), "patch");
  assert.equal(versionChangeKind("unknown", "1.2.8"), "unknown");
});

test("builds useful narratives for outdated and vulnerable findings", () => {
  const outdatedNarrative = buildFindingNarrative(outdated);
  assert.match(outdatedNarrative.impact, /version mayor/i);
  assert.match(outdatedNarrative.recommendation, /pruebas/i);

  const vulnerabilityNarrative = buildFindingNarrative({
    ...outdated,
    kind: "vulnerability",
    severity: "CRITICAL",
    vulnerability: {
      cveId: "CVE-2026-0001",
      severity: "CRITICAL",
      description: "Remote execution issue"
    }
  });
  assert.equal(vulnerabilityNarrative.summary, "Remote execution issue");
  assert.match(vulnerabilityNarrative.impact, /prioridad inmediata/i);
});
