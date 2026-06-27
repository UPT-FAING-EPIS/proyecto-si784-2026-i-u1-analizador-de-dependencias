import assert from "node:assert/strict";
import test from "node:test";
import { buildFindingDetailsHtml } from "./finding-details-panel.js";
import type { Finding } from "./models.js";

const unresolved: Finding = {
  kind: "outdated",
  groupId: "org.example",
  artifactId: "<unsafe>",
  coordinate: "org.example:<unsafe>",
  currentVersion: "unknown",
  latestVersion: "2.0.0",
  ecosystem: "MAVEN"
};

test("renders unresolved versions with guidance and no unsafe update", () => {
  const html = buildFindingDetailsHtml(unresolved, "nonce");
  assert.match(html, /Version no detectada/);
  assert.match(html, /Activar analisis dinamico y reanalizar/);
  assert.doesNotMatch(html, /data-command="prepareUpdate"/);
  assert.match(html, /data-tone="outdated"/);
  assert.doesNotMatch(html, /org\.example:<unsafe>/);
  assert.match(html, /org\.example:&lt;unsafe&gt;/);
});

test("renders the prepare action for a safe update", () => {
  const html = buildFindingDetailsHtml({
    ...unresolved,
    currentVersion: "1.0.0",
    relationship: "transitive",
    directRoot: "org.example:root:1.0.0",
    chainClassification: "RUNTIME",
    dependencyChain: ["org.example:root:1.0.0", "org.example:artifact:1.0.0"]
  }, "nonce");
  assert.match(html, /data-command="prepareUpdate"/);
  assert.doesNotMatch(html, /data-command="enableDynamic"/);
  assert.match(html, /Transitiva/);
  assert.match(html, /org\.example:root:1\.0\.0/);
  assert.match(html, /RUNTIME/);
});
