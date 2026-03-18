import { defineConfig, devices } from "@playwright/test";
import path from "path";

const STORAGE_STATE = path.join(__dirname, ".auth", "admin.json");

export default defineConfig({
  testDir: "./tests",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [["html", { open: "never" }], ["list"]],
  timeout: 60_000,
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL: "http://localhost:3001",
    storageState: STORAGE_STATE,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },

  globalSetup: require.resolve("./global-setup"),
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
    {
      name: "firefox",
      use: { ...devices["Desktop Firefox"] },
    },
    {
      name: "webkit",
      use: { ...devices["Desktop Safari"] },
    },
  ],
});

export { STORAGE_STATE };
