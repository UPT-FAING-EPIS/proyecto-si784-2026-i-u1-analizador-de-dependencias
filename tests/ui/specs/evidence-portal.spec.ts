import { expect, test } from "@playwright/test";

test("muestra la portada documental y los cinco informes", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveTitle(/DepAnalyzer/i);
  await expect(page.getByRole("heading", { level: 1 })).toContainText("DepAnalyzer");
  await expect(page.getByRole("link", { name: /FD01/ })).toBeVisible();
  await expect(page.getByRole("link", { name: /FD05/ })).toBeVisible();
});

test("permite navegar al manual de usuario", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("link", { name: /Manual de Usuario/ }).click();
  await expect(page).toHaveURL(/Manual-de-Usuario\.html$/);
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Manual de Usuario");
});

test("publica el índice de reportes de calidad", async ({ page }) => {
  await page.goto("/reports/");
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Reportes");
  for (const report of ["Cobertura", "Mutación", "BDD", "Interfaz", "Semgrep", "Snyk", "Sonar"]) {
    await expect(page.getByRole("link", { name: new RegExp(report, "i") })).toBeVisible();
  }
});

test("mantiene legible el portal en viewport móvil", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(page.locator("main.page")).toBeVisible();
  await expect(page.locator("body")).not.toHaveCSS("overflow-x", "scroll");
});
