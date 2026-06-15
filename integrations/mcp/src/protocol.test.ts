import assert from "node:assert/strict";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

test("stdio server advertises all DepAnalyzer tools", async () => {
  const currentDir = path.dirname(fileURLToPath(import.meta.url));
  const serverEntry = path.join(currentDir, "index.js");
  const transport = new StdioClientTransport({
    command: process.execPath,
    args: [serverEntry],
    stderr: "pipe"
  });
  const client = new Client({
    name: "depanalyzer-mcp-test",
    version: "0.1.0"
  });

  try {
    await client.connect(transport);
    const result = await client.listTools();
    const toolNames = result.tools.map((tool) => tool.name).sort();

    assert.deepEqual(toolNames, [
      "analyze_dependencies",
      "apply_dependency_updates",
      "plan_dependency_updates"
    ]);

    const invalidPathResult = await client.callTool({
      name: "analyze_dependencies",
      arguments: {
        project_path: path.join(currentDir, "missing-project")
      }
    });
    assert.equal(invalidPathResult.isError, true);
  } finally {
    await client.close();
  }
});
