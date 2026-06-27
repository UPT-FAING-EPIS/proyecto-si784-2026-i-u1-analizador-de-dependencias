import * as vscode from "vscode";
import { DepAnalyzerCli } from "./cli.js";
import { DepAnalyzerController } from "./controller.js";
import { FindingsProvider } from "./findings-view.js";
import { isSupportedDependencyFile } from "./report-utils.js";
import type { UpdateCandidate } from "./models.js";

export function activate(context: vscode.ExtensionContext): void {
  const output = vscode.window.createOutputChannel("DepAnalyzer");
  const findingsProvider = new FindingsProvider();
  const cli = new DepAnalyzerCli(context, output);
  const controller = new DepAnalyzerController(context, cli, findingsProvider, output);

  context.subscriptions.push(
    output,
    controller,
    vscode.window.registerTreeDataProvider("depanalyzer.findings", findingsProvider),
    vscode.commands.registerCommand("depanalyzer.scanWorkspace", () => controller.analyzeWorkspace()),
    vscode.commands.registerCommand("depanalyzer.showOutput", () => controller.showOutput()),
    vscode.commands.registerCommand("depanalyzer.scanFile", () => {
      const editor = vscode.window.activeTextEditor;
      if (editor) return controller.analyzeDocument(editor.document);
      return controller.analyzeWorkspace();
    }),
    vscode.commands.registerCommand("depanalyzer.clearDiagnostics", () => controller.clear()),
    vscode.commands.registerCommand("depanalyzer.showFindingDetails", (arg) => controller.showFindingDetails(arg)),
    vscode.commands.registerCommand("depanalyzer.openFindingLocation", (arg) => controller.openFindingLocation(arg)),
    vscode.commands.registerCommand("depanalyzer.openFindingReference", (arg) => controller.openFindingReference(arg)),
    vscode.commands.registerCommand("depanalyzer.applyUpdate", (candidate?: UpdateCandidate) => {
      if (!candidate) {
        void vscode.window.showWarningMessage("Usa el Quick Fix de DepAnalyzer sobre un diagnostico para aplicar actualizaciones.");
        return undefined;
      }
      return controller.applyUpdate(candidate);
    }),
    vscode.languages.registerHoverProvider({ scheme: "file" }, controller),
    vscode.languages.registerCodeActionsProvider({ scheme: "file" }, controller, {
      providedCodeActionKinds: [vscode.CodeActionKind.QuickFix]
    }),
    vscode.workspace.onDidSaveTextDocument((document) => {
      const config = vscode.workspace.getConfiguration("depanalyzer");
      if (config.get<boolean>("scanOnSave", true) && isSupportedDependencyFile(document.fileName)) {
        void controller.analyzeDocument(document);
      }
    })
  );

  const config = vscode.workspace.getConfiguration("depanalyzer");
  if (config.get<boolean>("autoAnalyze", true)) {
    void controller.analyzeWorkspace();
  }
}

export function deactivate(): void {
  // VS Code disposes subscriptions registered during activation.
}
