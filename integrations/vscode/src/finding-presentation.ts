import type { Finding } from "./models.js";

export type FindingTone = "critical" | "high" | "medium" | "low" | "unknown" | "outdated";
export type VersionChangeKind = "major" | "minor" | "patch" | "other" | "unknown";

export interface FindingNarrative {
  summary: string;
  impact: string;
  recommendation: string;
}

const UNRESOLVED_VERSIONS = new Set(["", "unknown", "n/a", "n/d", "nd", "none", "null"]);

export function isVersionResolved(version?: string): boolean {
  return version !== undefined && !UNRESOLVED_VERSIONS.has(version.trim().toLowerCase());
}

export function displayVersion(version?: string): string {
  return isVersionResolved(version) ? version!.trim() : "Version no detectada";
}

export function canSafelyUpdate(finding: Finding): boolean {
  return isVersionResolved(finding.currentVersion) && isVersionResolved(finding.latestVersion);
}

export function findingTone(finding: Finding): FindingTone {
  if (finding.kind === "outdated") return "outdated";
  return finding.severity?.toLowerCase() as FindingTone | undefined ?? "unknown";
}

export function versionChangeKind(currentVersion?: string, nextVersion?: string): VersionChangeKind {
  if (!isVersionResolved(currentVersion) || !isVersionResolved(nextVersion)) return "unknown";

  const current = numericVersion(currentVersion!);
  const next = numericVersion(nextVersion!);
  if (!current || !next) return "other";
  if (current[0] !== next[0]) return "major";
  if (current[1] !== next[1]) return "minor";
  if (current[2] !== next[2]) return "patch";
  return "other";
}

export function buildFindingNarrative(finding: Finding): FindingNarrative {
  if (finding.kind === "vulnerability") {
    const providerDescription = finding.vulnerability?.description?.trim();
    const severity = finding.severity ?? "UNKNOWN";
    return {
      summary: providerDescription ||
        `DepAnalyzer detecto una vulnerabilidad ${severity.toLowerCase()} en ${finding.coordinate}.`,
      impact: vulnerabilityImpact(severity),
      recommendation: finding.latestVersion && isVersionResolved(finding.latestVersion)
        ? `Revisa la referencia y prepara la actualizacion a ${finding.latestVersion}. Ejecuta las pruebas del proyecto antes de publicar el cambio.`
        : "Revisa la referencia de seguridad y valida una version corregida antes de modificar la dependencia."
    };
  }

  if (!isVersionResolved(finding.currentVersion)) {
    return {
      summary: `Existe una version disponible (${finding.latestVersion ?? "no especificada"}), pero la version usada por el proyecto no pudo determinarse con el analisis actual.`,
      impact: "La version puede estar administrada por Maven, Gradle, un catalogo o una propiedad externa. Sin conocerla no es seguro reemplazarla automaticamente.",
      recommendation: "Activa el analisis dinamico y vuelve a analizar. DepAnalyzer habilitara la actualizacion solo cuando pueda verificar la version de origen."
    };
  }

  const change = versionChangeKind(finding.currentVersion, finding.latestVersion);
  return {
    summary: `La dependencia puede actualizarse de ${finding.currentVersion} a ${finding.latestVersion ?? "una version mas reciente"}.`,
    impact: versionChangeImpact(change),
    recommendation: change === "major"
      ? "Revisa la guia de migracion y ejecuta todas las pruebas: una actualizacion mayor puede incluir cambios incompatibles."
      : "Revisa el changelog de la dependencia y ejecuta las pruebas del proyecto despues de aplicar el cambio."
  };
}

export function changeKindLabel(kind: VersionChangeKind): string {
  switch (kind) {
    case "major": return "Cambio mayor";
    case "minor": return "Cambio menor";
    case "patch": return "Parche";
    case "other": return "Actualizacion";
    case "unknown": return "Cambio por verificar";
  }
}

function numericVersion(value: string): [number, number, number] | undefined {
  const match = value.trim().match(/^[v=~^><\s]*?(\d+)(?:\.(\d+))?(?:\.(\d+))?/i);
  if (!match) return undefined;
  return [
    Number(match[1]),
    Number(match[2] ?? 0),
    Number(match[3] ?? 0)
  ];
}

function vulnerabilityImpact(severity: NonNullable<Finding["severity"]>): string {
  switch (severity) {
    case "CRITICAL":
      return "Prioridad inmediata. Una vulnerabilidad critica puede permitir comprometer el sistema o sus datos.";
    case "HIGH":
      return "Prioridad alta. Conviene corregirla antes de la siguiente entrega y limitar la exposicion mientras tanto.";
    case "MEDIUM":
      return "Prioridad media. Evalua si la funcionalidad vulnerable esta expuesta y programa la correccion.";
    case "LOW":
      return "Prioridad baja. El riesgo es menor, pero debe incluirse en el mantenimiento regular.";
    case "UNKNOWN":
      return "La fuente no entrego una severidad concluyente. Revisa la referencia antes de decidir la prioridad.";
  }
}

function versionChangeImpact(kind: VersionChangeKind): string {
  switch (kind) {
    case "major":
      return "Es un salto de version mayor y puede introducir cambios incompatibles en APIs o configuracion.";
    case "minor":
      return "Es una actualizacion menor; normalmente incorpora funciones y correcciones manteniendo compatibilidad.";
    case "patch":
      return "Es una actualizacion de parche; normalmente contiene correcciones compatibles y de bajo impacto.";
    case "other":
      return "El formato de version no permite clasificar el salto con precision. Revisa el changelog antes de actualizar.";
    case "unknown":
      return "No se puede medir el cambio porque falta una de las versiones.";
  }
}
