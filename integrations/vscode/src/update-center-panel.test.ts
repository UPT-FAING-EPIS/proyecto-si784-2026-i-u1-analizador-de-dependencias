import assert from "node:assert/strict";
import test from "node:test";
import type { UpdatePlan } from "./models.js";
import { buildUpdateCenterHtml } from "./update-center-panel.js";

const plan: UpdatePlan = {
  schemaVersion: "1.0",
  projectType: "MAVEN",
  buildFile: "C:\\project\\pom.xml",
  suggestions: [{
    id: "safe-id",
    groupId: "org.example",
    artifactId: "safe",
    currentVersion: "1.0.0",
    newVersion: "2.0.0",
    reason: "CVE",
    targetType: "DIRECT",
    ecosystem: "MAVEN"
  }, {
    id: "unknown-id",
    groupId: "org.example",
    artifactId: "unknown",
    currentVersion: "unknown",
    newVersion: "2.0.0",
    reason: "OUTDATED",
    targetType: "DIRECT",
    ecosystem: "MAVEN"
  }]
};

test("starts with no bulk updates selected and disables unresolved versions", () => {
  const html = buildUpdateCenterHtml(plan, "nonce");
  assert.doesNotMatch(html, /\n          checked\n/);
  assert.match(html, /value="unknown-id"[\s\S]*?disabled/);
  assert.match(html, /Activar analisis dinamico/);
});

test("preselects only the requested safe update", () => {
  const html = buildUpdateCenterHtml(plan, "nonce", "safe-id");
  const inputs = html.match(/<input[\s\S]*?>/g) ?? [];
  const safeInput = inputs.find((input) => input.includes('value="safe-id"')) ?? "";
  const unknownInput = inputs.find((input) => input.includes('value="unknown-id"')) ?? "";
  assert.match(safeInput, /\bchecked\b/);
  assert.doesNotMatch(unknownInput, /\bchecked\b/);
});
