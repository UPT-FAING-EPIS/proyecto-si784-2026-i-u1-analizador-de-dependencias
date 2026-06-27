import { isVersionResolved } from "./finding-presentation.js";
import type { UpdateCandidate, UpdateSuggestion } from "./models.js";

export function isSuggestionSafe(suggestion: UpdateSuggestion): boolean {
  return isVersionResolved(suggestion.currentVersion) && isVersionResolved(suggestion.newVersion);
}

export function sortUpdateSuggestions(suggestions: UpdateSuggestion[]): UpdateSuggestion[] {
  return [...suggestions].sort((left, right) => {
    const leftSecurity = left.reason.toUpperCase() === "CVE" ? 0 : 1;
    const rightSecurity = right.reason.toUpperCase() === "CVE" ? 0 : 1;
    return leftSecurity - rightSecurity ||
      coordinateFor(left).localeCompare(coordinateFor(right));
  });
}

export function findMatchingSuggestion(
  suggestions: UpdateSuggestion[],
  candidate?: UpdateCandidate
): UpdateSuggestion | undefined {
  if (!candidate) return undefined;
  return suggestions.find((suggestion) =>
    suggestion.groupId === candidate.groupId &&
    suggestion.artifactId === candidate.artifactId &&
    suggestion.currentVersion === candidate.currentVersion &&
    suggestion.newVersion === candidate.newVersion &&
    (!candidate.ecosystem || suggestion.ecosystem === candidate.ecosystem)
  );
}

export function buildApplyUpdateArgs(
  projectPath: string,
  suggestionIds: string[],
  dynamic: boolean
): string[] {
  const uniqueIds = [...new Set(suggestionIds.map((id) => id.trim()).filter(Boolean))];
  if (uniqueIds.length === 0) {
    throw new Error("Selecciona al menos una actualizacion.");
  }

  const args = ["--no-telemetry", "update", projectPath];
  for (const id of uniqueIds) {
    args.push("--apply-id", id);
  }
  if (dynamic) args.push("--dynamic");
  return args;
}

export function coordinateFor(suggestion: UpdateSuggestion): string {
  if (suggestion.ecosystem === "NPM") {
    return suggestion.groupId === "npm"
      ? suggestion.artifactId
      : `${suggestion.groupId}/${suggestion.artifactId}`;
  }
  if (suggestion.ecosystem === "PYPI") return suggestion.artifactId;
  return `${suggestion.groupId}:${suggestion.artifactId}`;
}
