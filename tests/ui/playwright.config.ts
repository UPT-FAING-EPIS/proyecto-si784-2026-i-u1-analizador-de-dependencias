import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./specs",
  outputDir: "../../build/test-results/playwright",
  reporter: [
    ["html", { outputFolder: "../../build/reports/playwright", open: "never" }],
    ["junit", { outputFile: "../../build/test-results/interface/playwright.xml" }],
    ["list"]
  ],
  use: {
    baseURL: "http://127.0.0.1:4173",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "on"
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] }
    }
  ],
  webServer: {
    command: "python -m http.server 4173 --bind 127.0.0.1 --directory ../../build/pages-site",
    url: "http://127.0.0.1:4173",
    reuseExistingServer: false
  }
});
