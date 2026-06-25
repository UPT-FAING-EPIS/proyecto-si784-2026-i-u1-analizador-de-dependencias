import assert from "node:assert/strict";
import test from "node:test";
import { detectCliCapabilities } from "./cli-capabilities.js";

test("detects the current stdout and update-plan CLI interface", () => {
  const capabilities = detectCliCapabilities(
    "--output-file <path>\n--quiet",
    "--plan\n--output-file <path>\n--apply-id <id>"
  );

  assert.deepEqual(capabilities, {
    analyzeStdout: true,
    updatePlan: true,
    applyById: true
  });
});

test("falls back for the public legacy CLI interface", () => {
  const capabilities = detectCliCapabilities(
    "--output=<text>\n--timeout=<int>",
    "--dry-run\n--only-security"
  );

  assert.deepEqual(capabilities, {
    analyzeStdout: false,
    updatePlan: false,
    applyById: false
  });
});
