export interface CliCapabilities {
  analyzeStdout: boolean;
  updatePlan: boolean;
  applyById: boolean;
}

export function detectCliCapabilities(analyzeHelp: string, updateHelp: string): CliCapabilities {
  return {
    analyzeStdout: analyzeHelp.includes("--output-file") && analyzeHelp.includes("--quiet"),
    updatePlan: updateHelp.includes("--plan") && updateHelp.includes("--output-file"),
    applyById: updateHelp.includes("--apply-id")
  };
}
