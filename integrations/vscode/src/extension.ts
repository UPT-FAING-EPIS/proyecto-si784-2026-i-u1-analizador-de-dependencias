import * as vscode from "vscode";
import { DepAnalyzerCli } from "./cli.js";
import { DepAnalyzerController } from "./controller.js";
import { DependencyTreeProvider, type DependencyTreeFilter } from "./dependency-tree-view.js";
import { FindingsProvider } from "./findings-view.js";
import { isSupportedDependencyFile } from "./report-utils.js";
import type { UpdateCandidate } from "./models.js";

export function activate(context: vscode.ExtensionContext): void {
  const output = vscode.window.createOutputChannel("DepAnalyzer");
  const findingsProvider = new FindingsProvider();
  const dependencyTreeProvider = new DependencyTreeProvider();
  const cli = new DepAnalyzerCli(context, output);
  const controller = new DepAnalyzerController(context, cli, findingsProvider, dependencyTreeProvider, output);

  context.subscriptions.push(
    output,
    controller,
    vscode.window.registerTreeDataProvider("depanalyzer.findings", findingsProvider),
    vscode.window.registerTreeDataProvider("depanalyzer.dependencyTree", dependencyTreeProvider),
    vscode.commands.registerCommand("depanalyzer.scanWorkspace", () => controller.analyzeWorkspace()),
    vscode.commands.registerCommand("depanalyzer.showOutput", () => controller.showOutput()),
    vscode.commands.registerCommand("depanalyzer.guideCliUpgrade", () => controller.guideCliUpgrade()),
    vscode.commands.registerCommand("depanalyzer.redetectCli", () => controller.resetCliCapabilities(true)),
    vscode.commands.registerCommand("depanalyzer.filterDependencyTree", async () => {
      const choices: Array<{ label: string; description: string; value: DependencyTreeFilter }> = [
        { label: "Todas", description: "Muestra el arbol completo", value: "all" },
        { label: "Problematicas", description: "CVE o actualizacion disponible", value: "problems" },
        { label: "Vulnerables", description: "Solo ramas con CVE", value: "vulnerable" },
        { label: "Directas", description: "Solo dependencias declaradas directamente", value: "direct" }
      ];
      const selected = await vscode.window.showQuickPick(choices, {
        title: "Filtrar arbol de dependencias",
        placeHolder: `Filtro actual: ${dependencyTreeProvider.getFilter()}`
      });
      if (selected) dependencyTreeProvider.setFilter(selected.value);
    }),
    vscode.commands.registerCommand("depanalyzer.scanFile", () => {
      const editor = vscode.window.activeTextEditor;
      if (editor) return controller.analyzeDocument(editor.document);
      return controller.analyzeWorkspace();
    }),
    vscode.commands.registerCommand("depanalyzer.clearDiagnostics", () => controller.clear()),
    vscode.commands.registerCommand("depanalyzer.showFindingDetails", (arg) => controller.showFindingDetails(arg)),
    vscode.commands.registerCommand("depanalyzer.openFindingLocation", (arg) => controller.openFindingLocation(arg)),
    vscode.commands.registerCommand("depanalyzer.openFindingReference", (arg) => controller.openFindingReference(arg)),
    vscode.commands.registerCommand(
      "depanalyzer.manageUpdates",
      (candidate?: UpdateCandidate) => controller.manageUpdates(candidate)
    ),
    vscode.commands.registerCommand(
      "depanalyzer.applyUpdate",
      (candidate?: UpdateCandidate) => controller.applyUpdate(candidate)
    ),
    vscode.languages.registerHoverProvider({ scheme: "file" }, controller),
    vscode.languages.registerCodeActionsProvider({ scheme: "file" }, controller, {
      providedCodeActionKinds: [vscode.CodeActionKind.QuickFix]
    }),
    vscode.workspace.onDidSaveTextDocument((document) => {
      const config = vscode.workspace.getConfiguration("depanalyzer");
      if (config.get<boolean>("scanOnSave", true) && isSupportedDependencyFile(document.fileName)) {
        controller.scheduleDocumentAnalysis(document);
      }
    }),
    vscode.workspace.onDidChangeConfiguration((event) => {
      if (event.affectsConfiguration("depanalyzer")) controller.resetCliCapabilities(false);
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
