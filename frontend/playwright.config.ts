import { defineConfig, devices } from "@playwright/test";

const externalFrontend = process.env.FRONTEND_BASE_URL;

export default defineConfig({
  testDir: "./tests",
  fullyParallel: true,
  workers: 2,
  reporter: "list",
  use: { baseURL: externalFrontend ?? "http://127.0.0.1:5173", trace: "retain-on-failure" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"], channel: "chrome" } }],
  ...(externalFrontend
    ? {}
    : {
        webServer: {
          command: "npm run dev",
          url: "http://127.0.0.1:5173",
          reuseExistingServer: !process.env.CI,
        },
      }),
});
