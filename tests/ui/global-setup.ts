import { test as setup, expect } from "@playwright/test";
import path from "path";

const STORAGE_STATE = path.join(__dirname, ".auth", "admin.json");

setup("authenticate as admin", async ({ page }) => {
  // Navigate to the login page
  await page.goto("/login");

  // Wait for the login form to be visible
  await expect(page.locator("form")).toBeVisible({ timeout: 15_000 });

  // Fill in credentials
  await page.locator("#email").fill("admin@athena.com");
  await page.locator("#password").fill("admin123");

  // Click Sign In
  await page.getByRole("button", { name: /sign in/i }).click();

  // Wait for navigation to the dashboard (URL becomes "/" or "/")
  await page.waitForURL("/", { timeout: 15_000 }).catch(() => {
    // Some setups redirect differently; wait for any non-login page
    return page.waitForURL(/^(?!.*\/login)/, { timeout: 10_000 });
  });

  // Verify we are authenticated — sidebar or dashboard heading should be visible
  await expect(
    page.getByText("Overview Dashboard").or(page.getByText("AthenaLMS"))
  ).toBeVisible({ timeout: 10_000 });

  // Save the authenticated storage state
  await page.context().storageState({ path: STORAGE_STATE });
});
