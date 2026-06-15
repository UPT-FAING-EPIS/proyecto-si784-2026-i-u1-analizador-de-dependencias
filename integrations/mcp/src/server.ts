import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import type { CallToolResult } from "@modelcontextprotocol/sdk/types.js";
import { ProcessCliRunner } from "./cli-runner.js";
import {
  analyzeInputSchema,
  applyInputSchema,
  planInputSchema
} from "./schemas.js";
import { DepAnalyzerService } from "./service.js";

export function createServer(
  service = new DepAnalyzerService(new ProcessCliRunner())
): McpServer {
  const server = new McpServer({
    name: "depanalyzer",
    version: "0.1.0"
  });

  server.registerTool(
    "analyze_dependencies",
    {
      title: "Analyze project dependencies",
      description: "Scans a supported project for outdated and vulnerable dependencies without modifying files.",
      inputSchema: analyzeInputSchema
    },
    async (input) => toolResult(async () => service.analyze({
      projectPath: input.project_path,
      provider: input.provider,
      dynamic: input.dynamic,
      includeChains: input.include_chains,
      timeoutSeconds: input.timeout_seconds
    }))
  );

  server.registerTool(
    "plan_dependency_updates",
    {
      title: "Plan dependency updates",
      description: "Returns stable update suggestion IDs without modifying project files.",
      inputSchema: planInputSchema
    },
    async (input) => toolResult(async () => service.planUpdates({
      projectPath: input.project_path,
      dynamic: input.dynamic,
      onlySecurity: input.only_security
    }))
  );

  server.registerTool(
    "apply_dependency_updates",
    {
      title: "Apply approved dependency updates",
      description: "Applies only suggestion IDs explicitly approved by the user and creates the CLI backup.",
      inputSchema: applyInputSchema
    },
    async (input) => toolResult(async () => service.applyUpdates({
      projectPath: input.project_path,
      suggestionIds: input.suggestion_ids,
      confirmed: input.confirmed,
      dynamic: input.dynamic,
      onlySecurity: input.only_security
    }))
  );

  return server;
}

async function toolResult(run: () => Promise<unknown>): Promise<CallToolResult> {
  try {
    const value = await run();
    return {
      content: [{
        type: "text",
        text: JSON.stringify(value, null, 2)
      }],
      structuredContent: value as Record<string, unknown>
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return {
      isError: true,
      content: [{
        type: "text",
        text: message
      }]
    };
  }
}
