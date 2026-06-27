import { access, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import * as vscode from "vscode";
import spawn from "cross-spawn";
import {
  capabilitiesFromDocument,
  detectCliCapabilities,
  parseCapabilityDocument,
  type CliCapabilities
} from "./cli-capabilities.js";
import type {
  AnalysisRunOptions,
  CliProgressEvent,
  DependencyReport,
  UpdatePlan
} from "./models.js";
import { buildApplyUpdateArgs } from "./update-presentation.js";

const MAX_OUTPUT_BYTES = 10 * 1024 * 1024;
const LEGACY_REPORT_NAME = "dependency-report.json";

export class DepAnalyzerCli {
  private readonly capabilities = new Map<string, Promise<CliCapabilities>>();

  constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly output: vscode.OutputChannel
  ) {}

  async analyze(projectPath: string, runOptions?: Partial<AnalysisRunOptions>): Promise<DependencyReport> {
    const config = vscode.workspace.getConfiguration("depanalyzer");
    const capabilities = await this.capabilitiesFor(projectPath);
    const options: AnalysisRunOptions = {
      dynamic: runOptions?.dynamic ?? config.get<boolean>("dynamic", false),
      includeChains: runOptions?.includeChains ?? true,
      timeoutSeconds: runOptions?.timeoutSeconds ?? config.get<number>("timeoutSeconds", 1800),
      cancellationToken: runOptions?.cancellationToken,
      onProgress: runOptions?.onProgress
    };
    const args = ["--no-telemetry", "analyze", projectPath, "--output", "json"];
    args.push("--timeout", String(options.timeoutSeconds));
    if (options.dynamic) args.push("--dynamic");
    if (options.dynamic && capabilities.progressJson) args.push("--command-output");
    if (options.includeChains && capabilities.vulnerabilityChains) args.push("--show-chains");
    if (capabilities.dependencyTree) args.push("--tree-expand", "all");
    if (capabilities.progressJson) args.push("--progress-json");
    const provider = config.get<string>("provider", "auto");
    if (provider === "oss") args.push("--oss");
    if (provider === "nvd") args.push("--nvd");

    if (capabilities.analyzeStdout) {
      return this.analyzeWithModernCli(args, projectPath, options);
    }

    return this.analyzeWithLegacyCli(args, projectPath, options);
  }

  async planUpdates(projectPath: string, dynamic?: boolean): Promise<UpdatePlan> {
    const capabilities = await this.capabilitiesFor(projectPath);
    if (!capabilities.updatePlan) {
      throw new Error(
        "La version instalada de DepAnalyzer no permite planes de actualizacion. " +
        "Actualiza el CLI cuando esa funcion este disponible."
      );
    }
    const config = vscode.workspace.getConfiguration("depanalyzer");
    const args = ["--no-telemetry", "update", projectPath, "--plan", "--output-file", "-"];
    if (dynamic ?? config.get<boolean>("dynamic", false)) args.push("--dynamic");

    const result = await this.run(args, projectPath);
    if (result.exitCode !== 0) {
      throw new Error(`DepAnalyzer update --plan fallo con codigo ${result.exitCode}: ${result.stderr.trim()}`);
    }
    return parseJson<UpdatePlan>(result.stdout, "plan de actualizacion");
  }

  async applyUpdate(projectPath: string, suggestionId: string, dynamic?: boolean): Promise<string> {
    return this.applyUpdates(projectPath, [suggestionId], dynamic);
  }

  async applyUpdates(projectPath: string, suggestionIds: string[], dynamic?: boolean): Promise<string> {
    const capabilities = await this.capabilitiesFor(projectPath);
    if (!capabilities.applyById) {
      throw new Error("La version instalada de DepAnalyzer no permite aplicar sugerencias por identificador.");
    }
    const config = vscode.workspace.getConfiguration("depanalyzer");
    const args = buildApplyUpdateArgs(
      projectPath,
      suggestionIds,
      dynamic ?? config.get<boolean>("dynamic", false)
    );

    const result = await this.run(args, projectPath);
    if (result.exitCode !== 0) {
      throw new Error(`DepAnalyzer update fallo con codigo ${result.exitCode}: ${result.stderr.trim()}`);
    }
    return result.stdout.trim();
  }

  async capabilitiesFor(cwd: string): Promise<CliCapabilities> {
    const executable = await this.resolveExecutable(cwd);
    const key = process.platform === "win32" ? executable.toLowerCase() : executable;
    let detected = this.capabilities.get(key);
    if (!detected) {
      detected = this.detectCapabilities(cwd);
      this.capabilities.set(key, detected);
    }
    return detected;
  }

  resetCapabilities(): void {
    this.capabilities.clear();
  }

  async executablePath(cwd?: string): Promise<string> {
    return this.resolveExecutable(cwd);
  }

  private async analyzeWithModernCli(
    args: string[],
    projectPath: string,
    options: AnalysisRunOptions
  ): Promise<DependencyReport> {
    const reportDirectory = vscode.Uri.joinPath(this.context.globalStorageUri, "reports");
    await vscode.workspace.fs.createDirectory(reportDirectory);
    const reportUri = vscode.Uri.joinPath(
      reportDirectory,
      `dependency-report-${Date.now()}-${Math.random().toString(16).slice(2)}.json`
    );
    args.push("--output-file", reportUri.fsPath, "--quiet");
    try {
      const result = await this.run(args, projectPath, {
        timeoutSeconds: options.timeoutSeconds,
        cancellationToken: options.cancellationToken,
        onProgress: options.onProgress,
        maxOutputBytes: Number.POSITIVE_INFINITY
      });
      if (result.exitCode !== 0) {
        throw new Error(`DepAnalyzer fallo con codigo ${result.exitCode}: ${result.stderr.trim()}`);
      }
      let raw: Uint8Array;
      try {
        raw = await vscode.workspace.fs.readFile(reportUri);
      } catch {
        throw new Error("DepAnalyzer termino sin generar el reporte JSON temporal.");
      }
      return parseReport(Buffer.from(raw).toString("utf8"));
    } finally {
      try {
        await vscode.workspace.fs.delete(reportUri, { useTrash: false });
      } catch {
        // The CLI may fail before creating the temporary report.
      }
    }
  }

  private async analyzeWithLegacyCli(
    args: string[],
    projectPath: string,
    options: AnalysisRunOptions
  ): Promise<DependencyReport> {
    const reportPath = path.join(projectPath, LEGACY_REPORT_NAME);
    const previousReport = await readFile(reportPath).catch(() => undefined);
    this.output.appendLine("CLI compatible detectado: leyendo el reporte JSON generado en el proyecto.");

    try {
      await rm(reportPath, { force: true });
      const result = await this.run(args, projectPath, {
        timeoutSeconds: options.timeoutSeconds,
        cancellationToken: options.cancellationToken
      });
      if (result.exitCode !== 0) {
        throw new Error(`DepAnalyzer fallo con codigo ${result.exitCode}: ${result.stderr.trim()}`);
      }
      const raw = await readFile(reportPath, "utf8").catch(() => {
        throw new Error(
          `DepAnalyzer termino sin generar ${LEGACY_REPORT_NAME}. Comprueba que el CLI admita --output json.`
        );
      });
      return parseReport(raw);
    } finally {
      if (previousReport) {
        await writeFile(reportPath, previousReport);
      } else {
        await rm(reportPath, { force: true });
      }
    }
  }

  private async detectCapabilities(cwd: string): Promise<CliCapabilities> {
    const capabilityResult = await this.run(
      ["--no-telemetry", "capabilities", "--output", "json"],
      cwd,
      { timeoutSeconds: 30 }
    );
    if (capabilityResult.exitCode === 0) {
      try {
        const capabilities = capabilitiesFromDocument(parseCapabilityDocument(capabilityResult.stdout));
        this.output.appendLine(
          `CLI ${capabilities.cliVersion}: contrato moderno, schemas=${capabilities.reportSchemas.join(",")}`
        );
        return capabilities;
      } catch (error) {
        this.output.appendLine(`Capacidades JSON no validas: ${(error as Error).message}`);
      }
    }

    const [analyzeHelp, updateHelp] = await Promise.all([
      this.run(["--no-telemetry", "analyze", "--help"], cwd, { timeoutSeconds: 30 }),
      this.run(["--no-telemetry", "update", "--help"], cwd, { timeoutSeconds: 30 })
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

  private async run(
    args: string[],
    cwd: string,
    options: {
      timeoutSeconds?: number;
      cancellationToken?: vscode.CancellationToken;
      onProgress?: (event: CliProgressEvent) => void;
      maxOutputBytes?: number;
    } = {}
  ): Promise<{ exitCode: number; stdout: string; stderr: string }> {
    const executable = await this.resolveExecutable(cwd);
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
      let stderrLineBuffer = "";
      let cancellation: vscode.Disposable | undefined;

      const fail = (error: Error): void => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        cancellation?.dispose();
        terminateProcessTree(child);
        reject(error);
      };

      const timeoutSeconds = options.timeoutSeconds ??
        vscode.workspace.getConfiguration("depanalyzer").get<number>("timeoutSeconds", 1800);
      const timer = setTimeout(() => {
        fail(new Error(`DepAnalyzer excedio ${timeoutSeconds} segundos`));
      }, timeoutSeconds * 1000);
      cancellation = options.cancellationToken?.onCancellationRequested(() => {
        fail(new Error("Analisis cancelado por el usuario."));
      });

      const append = (target: "stdout" | "stderr", chunk: Buffer) => {
        outputBytes += chunk.length;
        const maxOutputBytes = options.maxOutputBytes ?? MAX_OUTPUT_BYTES;
        if (outputBytes > maxOutputBytes) {
          fail(new Error(`La salida de DepAnalyzer excedio ${formatBytes(maxOutputBytes)}`));
          return;
        }
        if (target === "stdout") stdout += chunk.toString("utf8");
        else stderr += chunk.toString("utf8");
      };

      if (!child.stdout || !child.stderr) {
        fail(new Error("No se pudo leer la salida del proceso DepAnalyzer"));
        return;
      }

      child.stdout.on("data", (chunk: Buffer) => append("stdout", chunk));
      child.stderr.on("data", (chunk: Buffer) => {
        const text = chunk.toString("utf8");
        this.output.append(text);
        append("stderr", chunk);
        stderrLineBuffer += text;
        const lines = stderrLineBuffer.split(/\r?\n/);
        stderrLineBuffer = lines.pop() ?? "";
        for (const line of lines) {
          const event = parseProgressEvent(line);
          if (event) options.onProgress?.(event);
        }
      });
      child.on("error", (error) => {
        fail(error);
      });
      child.on("close", (exitCode) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        cancellation?.dispose();
        const finalEvent = parseProgressEvent(stderrLineBuffer);
        if (finalEvent) options.onProgress?.(finalEvent);
        resolve({ exitCode: exitCode ?? 2, stdout, stderr });
      });
    });
  }

  private async resolveExecutable(cwd?: string): Promise<string> {
    const scope = cwd ? vscode.Uri.file(cwd) : undefined;
    const configured = vscode.workspace
      .getConfiguration("depanalyzer", scope)
      .get<string>("executablePath", "")
      .trim();
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

function terminateProcessTree(child: ReturnType<typeof spawn>): void {
  if (!child.pid) return;
  if (process.platform === "win32") {
    const killer = spawn("taskkill", ["/pid", String(child.pid), "/T", "/F"], {
      windowsHide: true,
      stdio: "ignore"
    });
    killer.on("error", () => child.kill());
    return;
  }
  child.kill("SIGTERM");
}

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes)) return "el limite permitido";
  return `${Math.ceil(bytes / (1024 * 1024))} MiB`;
}

function parseReport(raw: string): DependencyReport {
  const report = parseJson<DependencyReport>(raw, "reporte de dependencias");
  if (typeof report.schemaVersion !== "string" || typeof report.projectName !== "string") {
    throw new Error("DepAnalyzer devolvio un reporte sin schemaVersion o projectName.");
  }
  return report;
}

function parseProgressEvent(line: string): CliProgressEvent | undefined {
  const trimmed = line.trim();
  if (!trimmed.startsWith("{")) return undefined;
  try {
    const value = JSON.parse(trimmed) as Partial<CliProgressEvent>;
    if (
      value.stream === "depanalyzer-progress" &&
      typeof value.type === "string" &&
      typeof value.message === "string" &&
      typeof value.timestamp === "string"
    ) {
      return value as CliProgressEvent;
    }
  } catch {
    // Non-JSON diagnostics remain available in the output channel.
  }
  return undefined;
}
