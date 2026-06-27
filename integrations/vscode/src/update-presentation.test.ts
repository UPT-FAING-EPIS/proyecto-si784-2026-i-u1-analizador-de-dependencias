import assert from "node:assert/strict";
import test from "node:test";
import type { UpdateSuggestion } from "./models.js";
import {
  buildApplyUpdateArgs,
  findMatchingSuggestion,
  isSuggestionSafe,
  sortUpdateSuggestions
} from "./update-presentation.js";

const outdated: UpdateSuggestion = {
  id: "outdated-id",
  groupId: "org.example",
  artifactId: "regular",
  currentVersion: "1.0.0",
  newVersion: "1.1.0",
  reason: "OUTDATED",
  targetType: "DIRECT",
  ecosystem: "MAVEN"
};

const security: UpdateSuggestion = {
  ...outdated,
  id: "security-id",
  artifactId: "security",
  reason: "CVE"
};

test("orders security updates before ordinary updates", () => {
  assert.deepEqual(
    sortUpdateSuggestions([outdated, security]).map((item) => item.id),
    ["security-id", "outdated-id"]
  );
});

test("builds one apply-id argument for every approved suggestion", () => {
  assert.deepEqual(
    buildApplyUpdateArgs("C:\\project", ["a", "b", "a"], true),
    ["--no-telemetry", "update", "C:\\project", "--apply-id", "a", "--apply-id", "b", "--dynamic"]
  );
  assert.throws(() => buildApplyUpdateArgs("C:\\project", [], false), /Selecciona/);
});

test("matches candidates and rejects unresolved suggestions", () => {
  assert.equal(isSuggestionSafe(outdated), true);
  assert.equal(isSuggestionSafe({ ...outdated, currentVersion: "unknown" }), false);
  assert.equal(findMatchingSuggestion([outdated], {
    projectPath: "C:\\project",
    groupId: outdated.groupId,
    artifactId: outdated.artifactId,
    currentVersion: outdated.currentVersion,
    newVersion: outdated.newVersion,
    ecosystem: outdated.ecosystem
  })?.id, outdated.id);
});
