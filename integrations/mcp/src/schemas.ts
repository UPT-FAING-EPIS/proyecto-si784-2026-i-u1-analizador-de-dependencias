import { z } from "zod";

export const providerModeSchema = z.enum(["auto", "oss", "nvd"]);

export const analyzeInputSchema = {
  project_path: z.string().min(1).describe("Absolute or current-working-directory-relative project directory"),
  provider: providerModeSchema.default("auto").describe("Vulnerability provider strategy"),
  dynamic: z.boolean().default(false).describe("Run Maven or Gradle dependency-tree analysis"),
  include_chains: z.boolean().default(true).describe("Include vulnerable dependency chains"),
  timeout_seconds: z.number().int().min(10).max(3600).default(1800)
};

export const planInputSchema = {
  project_path: z.string().min(1).describe("Absolute or current-working-directory-relative project directory"),
  dynamic: z.boolean().default(false).describe("Include dynamic transitive dependency analysis"),
  only_security: z.boolean().default(false).describe("Return only updates associated with CVEs")
};

export const applyInputSchema = {
  project_path: z.string().min(1).describe("Absolute or current-working-directory-relative project directory"),
  suggestion_ids: z.array(z.string().min(1)).min(1).max(100),
  confirmed: z.literal(true).describe("Must be true after the user explicitly approves these suggestion IDs"),
  dynamic: z.boolean().default(false).describe("Recalculate the plan with dynamic dependency analysis"),
  only_security: z.boolean().default(false).describe("Restrict recalculation to security updates")
};

export const analysisReportSchema = z.object({
  schemaVersion: z.string(),
  projectName: z.string()
}).passthrough();

export const updatePlanSchema = z.object({
  schemaVersion: z.string(),
  projectType: z.string(),
  buildFile: z.string(),
  suggestions: z.array(
    z.object({
      id: z.string(),
      groupId: z.string(),
      artifactId: z.string(),
      currentVersion: z.string(),
      newVersion: z.string(),
      reason: z.string(),
      targetType: z.string(),
      viaDirectCoordinate: z.string().nullable().optional(),
      ecosystem: z.string()
    }).passthrough()
  )
}).passthrough();

export type ProviderMode = z.infer<typeof providerModeSchema>;
export type AnalysisReport = z.infer<typeof analysisReportSchema>;
export type UpdatePlan = z.infer<typeof updatePlanSchema>;
