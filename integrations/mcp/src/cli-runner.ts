import { access, stat } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import spawn from "cross-spawn";

const MAX_OUTPUT_BYTES = 10 * 1024 * 1024;

export interface CliExecution {
  exitCode: number;
  stdout: string;
  stderr: string;
}

export interface CliRunner {
  run(args: string[], timeoutMs: number): Promise<CliExecution>;
}

export class CliExecutionError extends Error {
  constructor(
    message: string,
    readonly exitCode: number | null,
    readonly stderr: string
  ) {
    super(message);
    this.name = "CliExecutionError";
  }
}

export async function resolveProjectPath(projectPath: string): Promise<string> {
  const resolved = path.resolve(projectPath);
  const details = await stat(resolved).catch(() => null);
  if (!details?.isDirectory()) {
    throw new Error(`Project path must be an existing directory: ${resolved}`);
  }
  return resolved;
}

export class ProcessCliRunner implements CliRunner {
  constructor(private readonly executable?: string) {}

  async run(args: string[], timeoutMs: number): Promise<CliExecution> {
    const executable = this.executable ?? await resolveExecutable();

    return new Promise((resolve, reject) => {
      const child = spawn(executable, args, {
        cwd: process.cwd(),
        env: process.env,
        windowsHide: true,
        stdio: ["ignore", "pipe", "pipe"]
      });
      let stdout = "";
      let stderr = "";
      let outputBytes = 0;
      let settled = false;
      let timer: NodeJS.Timeout;

      const finishWithError = (error: Error) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        child.kill();
        reject(error);
      };

      const append = (target: "stdout" | "stderr", chunk: Buffer) => {
        outputBytes += chunk.length;
        if (outputBytes > MAX_OUTPUT_BYTES) {
          finishWithError(new Error("DepAnalyzer output exceeded 10 MiB"));
          return;
        }
        if (target === "stdout") stdout += chunk.toString("utf8");
        else stderr += chunk.toString("utf8");
      };

      if (!child.stdout || !child.stderr) {
        finishWithError(new Error("DepAnalyzer process streams were not available"));
        return;
      }

      child.stdout.on("data", (chunk: Buffer) => append("stdout", chunk));
      child.stderr.on("data", (chunk: Buffer) => append("stderr", chunk));
      child.on("error", finishWithError);
      child.on("close", (exitCode) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        resolve({ exitCode: exitCode ?? 2, stdout, stderr });
      });

      timer = setTimeout(() => {
        finishWithError(new Error(`DepAnalyzer timed out after ${timeoutMs} ms`));
      }, timeoutMs);
    });
  }
}

async function resolveExecutable(): Promise<string> {
  const configured = process.env.DEPANALYZER_BIN?.trim();
  if (configured) {
    await access(configured);
    return configured;
  }

  const moduleDir = path.dirname(fileURLToPath(import.meta.url));
  const repositoryRoot = path.resolve(moduleDir, "..", "..", "..");
  const scriptName = process.platform === "win32" ? "depanalyzer.bat" : "depanalyzer";
  const distributionScript = path.join(repositoryRoot, "build", "install", "depanalyzer", "bin", scriptName);
  if (await access(distributionScript).then(() => true).catch(() => false)) {
    return distributionScript;
  }

  return process.platform === "win32" ? "depanalyzer.exe" : "depanalyzer";
}
