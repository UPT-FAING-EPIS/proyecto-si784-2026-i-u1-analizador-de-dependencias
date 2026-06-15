import assert from "node:assert/strict";
import { mkdtemp } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import type { CliExecution, CliRunner } from "./cli-runner.js";
import { DepAnalyzerService } from "./service.js";

class FakeRunner implements CliRunner {
  readonly calls: string[][] = [];

  constructor(private readonly responses: CliExecution[]) {}

  async run(args: string[]): Promise<CliExecution> {
    this.calls.push(args);
    const response = this.responses.shift();
    if (!response) throw new Error("Unexpected CLI invocation");
    return response;
  }
}

async function temporaryProject(): Promise<string> {
  return mkdtemp(path.join(tmpdir(), "depanalyzer-mcp-"));
}

test("analyze returns parsed structured report and safe CLI arguments", async () => {
  const projectPath = await temporaryProject();
  const runner = new FakeRunner([{
    exitCode: 0,
    stdout: JSON.stringify({ schemaVersion: "1.0", projectName: "demo", directVulnerable: [] }),
    stderr: ""
  }]);
  const service = new DepAnalyzerService(runner);

  const report = await service.analyze({
    projectPath,
    provider: "oss",
    dynamic: true,
    includeChains: true,
    timeoutSeconds: 120
  });

  assert.equal(report.projectName, "demo");
  assert.deepEqual(
    runner.calls[0],
    [
      "--no-telemetry",
      "analyze",
      projectPath,
      "--output",
      "json",
      "--output-file",
      "-",
      "--quiet",
      "--timeout",
      "120",
      "--dynamic",
      "--show-chains",
      "--oss"
    ]
  );
});

test("plan returns update suggestion IDs without applying changes", async () => {
  const projectPath = await temporaryProject();
  const plan = {
    schemaVersion: "1.0",
    projectType: "MAVEN",
    buildFile: path.join(projectPath, "pom.xml"),
    suggestions: [{
      id: "abc123",
      groupId: "org.example",
      artifactId: "demo",
      currentVersion: "1.0.0",
      newVersion: "1.1.0",
      reason: "CVE",
      targetType: "DIRECT",
      ecosystem: "MAVEN"
    }]
  };
  const runner = new FakeRunner([{ exitCode: 0, stdout: JSON.stringify(plan), stderr: "" }]);

  const result = await new DepAnalyzerService(runner).planUpdates({
    projectPath,
    dynamic: false,
    onlySecurity: true
  });

  assert.equal(result.suggestions[0]?.id, "abc123");
  assert.ok(runner.calls[0]?.includes("--only-security"));
});

test("apply recalculates the plan and applies only approved IDs", async () => {
  const projectPath = await temporaryProject();
  const plan = {
    schemaVersion: "1.0",
    projectType: "NPM",
    buildFile: path.join(projectPath, "package.json"),
    suggestions: [{
      id: "approved-id",
      groupId: "npm",
      artifactId: "demo",
      currentVersion: "1.0.0",
      newVersion: "2.0.0",
      reason: "CVE",
      targetType: "DIRECT",
      ecosystem: "NPM"
    }]
  };
  const runner = new FakeRunner([
    { exitCode: 0, stdout: JSON.stringify(plan), stderr: "" },
    { exitCode: 0, stdout: "Resumen final: aplicadas=1, omitidas=0", stderr: "" }
  ]);

  const result = await new DepAnalyzerService(runner).applyUpdates({
    projectPath,
    suggestionIds: ["approved-id"],
    confirmed: true,
    dynamic: false,
    onlySecurity: false
  });

  assert.deepEqual(result.appliedSuggestionIds, ["approved-id"]);
  assert.deepEqual(runner.calls[1]?.slice(-2), ["--apply-id", "approved-id"]);
});

test("apply rejects stale suggestion IDs before modifying files", async () => {
  const projectPath = await temporaryProject();
  const runner = new FakeRunner([{
    exitCode: 0,
    stdout: JSON.stringify({
      schemaVersion: "1.0",
      projectType: "MAVEN",
      buildFile: path.join(projectPath, "pom.xml"),
      suggestions: []
    }),
    stderr: ""
  }]);

  await assert.rejects(
    () => new DepAnalyzerService(runner).applyUpdates({
      projectPath,
      suggestionIds: ["stale-id"],
      confirmed: true,
      dynamic: false,
      onlySecurity: false
    }),
    /stale or unavailable/
  );
  assert.equal(runner.calls.length, 1);
});

test("analysis rejects invalid project paths", async () => {
  const runner = new FakeRunner([]);
  await assert.rejects(
    () => new DepAnalyzerService(runner).analyze({
      projectPath: path.join(tmpdir(), "missing-depanalyzer-project"),
      provider: "auto",
      dynamic: false,
      includeChains: false,
      timeoutSeconds: 60
    }),
    /existing directory/
  );
});
