import { access, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import * as vscode from "vscode";
import spawn from "cross-spawn";
import { detectCliCapabilities, type CliCapabilities } from "./cli-capabilities.js";
import type { DependencyReport, UpdatePlan } from "./models.js";
import { buildApplyUpdateArgs } from "./update-presentation.js";

const MAX_OUTPUT_BYTES = 10 * 1024 * 1024;
const LEGACY_REPORT_NAME = "dependency-report.json";

export class DepAnalyzerCli {
  private capabilities: Promise<CliCapabilities> | undefined;

  constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly output: vscode.OutputChannel
  ) {}

  async analyze(projectPath: string): Promise<DependencyReport> {
    const config = vscode.workspace.getConfiguration("depanalyzer");
    const capabilities = await this.capabilitiesFor(projectPath);
    const args = ["--no-telemetry", "analyze", projectPath, "--output", "json"];
    if (capabilities.analyzeStdout) {
      args.push("--output-file", "-", "--quiet");
    }
    args.push("--timeout", String(config.get<number>("timeoutSeconds", 900)));

    if (config.get<boolean>("dynamic", false)) args.push("--dynamic");
    const provider = config.get<string>("provider", "auto");
    if (provider === "oss") args.push("--oss");
    if (provider === "nvd") args.push("--nvd");

    if (capabilities.analyzeStdout) {
      const result = await this.run(args, projectPath);
      if (result.exitCode !== 0) {
        throw new Error(`DepAnalyzer fallo con codigo ${result.exitCode}: ${result.stderr.trim()}`);
      }
      return parseJson<DependencyReport>(result.stdout, "reporte de dependencias");
    }

    return this.analyzeWithLegacyCli(args, projectPath);
  }

  async planUpdates(projectPath: string): Promise<UpdatePlan> {
    const capabilities = await this.capabilitiesFor(projectPath);
    if (!capabilities.updatePlan) {
      throw new Error(
        "La version instalada de DepAnalyzer no permite planes de actualizacion. " +
        "Actualiza el CLI cuando esa funcion este disponible."
      );
    }
    const config = vscode.workspace.getConfiguration("depanalyzer");
    const args = ["--no-telemetry", "update", projectPath, "--plan", "--output-file", "-"];
    if (config.get<boolean>("dynamic", false)) args.push("--dynamic");

    const result = await this.run(args, projectPath);
    if (result.exitCode !== 0) {
      throw new Error(`DepAnalyzer update --plan fallo con codigo ${result.exitCode}: ${result.stderr.trim()}`);
    }
    return parseJson<UpdatePlan>(result.stdout, "plan de actualizacion");
  }

  async applyUpdate(projectPath: string, suggestionId: string): Promise<string> {
    return this.applyUpdates(projectPath, [suggestionId]);
  }

  async applyUpdates(projectPath: string, suggestionIds: string[]): Promise<string> {
    const capabilities = await this.capabilitiesFor(projectPath);
    if (!capabilities.applyById) {
      throw new Error("La version instalada de DepAnalyzer no permite aplicar sugerencias por identificador.");
    }
    const config = vscode.workspace.getConfiguration("depanalyzer");
    const args = buildApplyUpdateArgs(
      projectPath,
      suggestionIds,
      config.get<boolean>("dynamic", false)
    );

    const result = await this.run(args, projectPath);
    if (result.exitCode !== 0) {
      throw new Error(`DepAnalyzer update fallo con codigo ${result.exitCode}: ${result.stderr.trim()}`);
    }
    return result.stdout.trim();
  }

  capabilitiesFor(cwd: string): Promise<CliCapabilities> {
    this.capabilities ??= this.detectCapabilities(cwd);
    return this.capabilities;
  }

  private async analyzeWithLegacyCli(args: string[], projectPath: string): Promise<DependencyReport> {
    const reportPath = path.join(projectPath, LEGACY_REPORT_NAME);
    const previousReport = await readFile(reportPath).catch(() => undefined);
    this.output.appendLine("CLI compatible detectado: leyendo el reporte JSON generado en el proyecto.");

    try {
      await rm(reportPath, { force: true });
      const result = await this.run(args, projectPath);
      if (result.exitCode !== 0) {
        throw new Error(`DepAnalyzer fallo con codigo ${result.exitCode}: ${result.stderr.trim()}`);
      }
      const raw = await readFile(reportPath, "utf8").catch(() => {
        throw new Error(
          `DepAnalyzer termino sin generar ${LEGACY_REPORT_NAME}. Comprueba que el CLI admita --output json.`
        );
      });
      return parseJson<DependencyReport>(raw, "reporte de dependencias");
    } finally {
      if (previousReport) {
        await writeFile(reportPath, previousReport);
      } else {
        await rm(reportPath, { force: true });
      }
    }
  }

  private async detectCapabilities(cwd: string): Promise<CliCapabilities> {
    const [analyzeHelp, updateHelp] = await Promise.all([
      this.run(["--no-telemetry", "analyze", "--help"], cwd),
      this.run(["--no-telemetry", "update", "--help"], cwd)
    ]);
    const analyzeText = `${analyzeHelp.stdout}\n${analyzeHelp.stderr}`;
    const updateText = `${updateHelp.stdout}\n${updateHelp.stderr}`;
    const capabilities = detectCliCapabilities(analyzeText, updateText);
    this.output.appendLine(
      `Compatibilidad CLI: stdout=${capabilities.analyzeStdout}, ` +
      `plan=${capabilities.updatePlan}, apply-id=${capabilities.applyById}`
    );
    return capabilities;
  }

  private async run(args: string[], cwd: string): Promise<{ exitCode: number; stdout: string; stderr: string }> {
    const executable = await this.resolveExecutable();
    this.output.appendLine(`> ${executable} ${args.join(" ")}`);

    return new Promise((resolve, reject) => {
      const child = spawn(executable, args, {
        cwd,
        env: process.env,
        windowsHide: true,
        stdio: ["ignore", "pipe", "pipe"]
      });
      let stdout = "";
      let stderr = "";
      let outputBytes = 0;
      let settled = false;

      const timeoutSeconds = vscode.workspace.getConfiguration("depanalyzer").get<number>("timeoutSeconds", 900);
      const timer = setTimeout(() => {
        if (settled) return;
        settled = true;
        child.kill();
        reject(new Error(`DepAnalyzer excedio ${timeoutSeconds} segundos`));
      }, timeoutSeconds * 1000);

      const append = (target: "stdout" | "stderr", chunk: Buffer) => {
        outputBytes += chunk.length;
        if (outputBytes > MAX_OUTPUT_BYTES) {
          if (!settled) {
            settled = true;
            clearTimeout(timer);
            child.kill();
            reject(new Error("La salida de DepAnalyzer excedio 10 MiB"));
          }
          return;
        }
        if (target === "stdout") stdout += chunk.toString("utf8");
        else stderr += chunk.toString("utf8");
      };

      if (!child.stdout || !child.stderr) {
        clearTimeout(timer);
        reject(new Error("No se pudo leer la salida del proceso DepAnalyzer"));
        return;
      }

      child.stdout.on("data", (chunk: Buffer) => append("stdout", chunk));
      child.stderr.on("data", (chunk: Buffer) => {
        const text = chunk.toString("utf8");
        this.output.append(text);
        append("stderr", chunk);
      });
      child.on("error", (error) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        reject(error);
      });
      child.on("close", (exitCode) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        resolve({ exitCode: exitCode ?? 2, stdout, stderr });
      });
    });
  }

  private async resolveExecutable(): Promise<string> {
    const configured = vscode.workspace.getConfiguration("depanalyzer").get<string>("executablePath", "").trim();
    if (configured) {
      await access(configured);
      return configured;
    }

    const repositoryRoot = path.resolve(this.context.extensionPath, "..", "..");
    const scriptName = process.platform === "win32" ? "depanalyzer.bat" : "depanalyzer";
    const distributionScript = path.join(repositoryRoot, "build", "install", "depanalyzer", "bin", scriptName);
    if (await access(distributionScript).then(() => true).catch(() => false)) {
      return distributionScript;
    }

    return process.platform === "win32" ? "depanalyzer.exe" : "depanalyzer";
  }
}

function parseJson<T>(raw: string, label: string): T {
  try {
    return JSON.parse(raw) as T;
  } catch (error) {
    throw new Error(`DepAnalyzer devolvio JSON invalido para ${label}: ${(error as Error).message}`);
  }
}
