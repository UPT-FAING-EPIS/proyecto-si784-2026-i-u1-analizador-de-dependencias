import {
  analysisReportSchema,
  type AnalysisReport,
  type ProviderMode,
  type UpdatePlan,
  updatePlanSchema
} from "./schemas.js";
import {
  CliExecutionError,
  type CliRunner,
  resolveProjectPath
} from "./cli-runner.js";

export interface AnalyzeOptions {
  projectPath: string;
  provider: ProviderMode;
  dynamic: boolean;
  includeChains: boolean;
  timeoutSeconds: number;
}

export interface PlanOptions {
  projectPath: string;
  dynamic: boolean;
  onlySecurity: boolean;
}

export interface ApplyOptions extends PlanOptions {
  suggestionIds: string[];
  confirmed: true;
}

export interface ApplyResult {
  appliedSuggestionIds: string[];
  stdout: string;
  stderr: string;
}

export class DepAnalyzerService {
  constructor(private readonly runner: CliRunner) {}

  async analyze(options: AnalyzeOptions): Promise<AnalysisReport> {
    const projectPath = await resolveProjectPath(options.projectPath);
    const args = [
      "--no-telemetry",
      "analyze",
      projectPath,
      "--output",
      "json",
      "--output-file",
      "-",
      "--quiet",
      "--timeout",
      options.timeoutSeconds.toString()
    ];
    if (options.dynamic) args.push("--dynamic");
    if (options.includeChains) args.push("--show-chains");
    if (options.provider === "oss") args.push("--oss");
    if (options.provider === "nvd") args.push("--nvd");

    const result = await this.runner.run(args, (options.timeoutSeconds + 10) * 1000);
    assertSuccessful(result.exitCode, result.stderr);
    return parseJson(result.stdout, analysisReportSchema, "analysis report");
  }

  async planUpdates(options: PlanOptions): Promise<UpdatePlan> {
    const projectPath = await resolveProjectPath(options.projectPath);
    const args = [
      "--no-telemetry",
      "update",
      projectPath,
      "--plan",
      "--output-file",
      "-"
    ];
    if (options.dynamic) args.push("--dynamic");
    if (options.onlySecurity) args.push("--only-security");

    const result = await this.runner.run(args, 31 * 60 * 1000);
    assertSuccessful(result.exitCode, result.stderr);
    return parseJson(result.stdout, updatePlanSchema, "update plan");
  }

  async applyUpdates(options: ApplyOptions): Promise<ApplyResult> {
    if (!options.confirmed) {
      throw new Error("Explicit confirmation is required before applying dependency updates");
    }
    const projectPath = await resolveProjectPath(options.projectPath);
    const plan = await this.planUpdates(options);
    const availableIds = new Set(plan.suggestions.map((suggestion) => suggestion.id));
    const missingIds = options.suggestionIds.filter((id) => !availableIds.has(id));
    if (missingIds.length > 0) {
      throw new Error(`Suggestion IDs are stale or unavailable: ${missingIds.join(", ")}`);
    }

    const args = ["--no-telemetry", "update", projectPath];
    if (options.dynamic) args.push("--dynamic");
    if (options.onlySecurity) args.push("--only-security");
    for (const id of options.suggestionIds) {
      args.push("--apply-id", id);
    }

    const result = await this.runner.run(args, 31 * 60 * 1000);
    assertSuccessful(result.exitCode, result.stderr);
    return {
      appliedSuggestionIds: [...options.suggestionIds],
      stdout: result.stdout.trim(),
      stderr: result.stderr.trim()
    };
  }
}

function assertSuccessful(exitCode: number, stderr: string): void {
  if (exitCode !== 0) {
    throw new CliExecutionError(
      `DepAnalyzer exited with code ${exitCode}${stderr.trim() ? `: ${stderr.trim()}` : ""}`,
      exitCode,
      stderr
    );
  }
}

function parseJson<T>(
  stdout: string,
  schema: { parse(value: unknown): T },
  label: string
): T {
  let value: unknown;
  try {
    value = JSON.parse(stdout);
  } catch (error) {
    throw new Error(`DepAnalyzer returned invalid JSON for ${label}: ${(error as Error).message}`);
  }
  return schema.parse(value);
}
