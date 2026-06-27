import assert from "node:assert/strict";
import test from "node:test";
import { matchesDependencyTreeFilter } from "./dependency-tree-utils.js";
import type { DependencyTreeNode } from "./models.js";

const vulnerableChild: DependencyTreeNode = {
  groupId: "org.example",
  artifactId: "child",
  currentVersion: "2.0.0",
  isDirectDependency: false,
  isDependencyManagement: false,
  vulnerabilities: [{ cveId: "CVE-2026-0003", severity: "HIGH" }],
  children: []
};

const root: DependencyTreeNode = {
  groupId: "org.example",
  artifactId: "root",
  currentVersion: "1.0.0",
  latestVersion: "1.1.0",
  isDirectDependency: true,
  isDependencyManagement: false,
  vulnerabilities: [],
  children: [vulnerableChild]
};

test("supports every dependency-tree filter", () => {
  assert.equal(matchesDependencyTreeFilter(root, "all"), true);
  assert.equal(matchesDependencyTreeFilter(root, "direct"), true);
  assert.equal(matchesDependencyTreeFilter(vulnerableChild, "direct"), false);
  assert.equal(matchesDependencyTreeFilter(root, "problems"), true);
  assert.equal(matchesDependencyTreeFilter(root, "vulnerable"), true);
  assert.equal(matchesDependencyTreeFilter(vulnerableChild, "vulnerable"), true);
});

test("keeps ancestors visible when only a descendant matches", () => {
  const cleanParent = { ...root, latestVersion: undefined };
  assert.equal(matchesDependencyTreeFilter(cleanParent, "vulnerable"), true);
});
