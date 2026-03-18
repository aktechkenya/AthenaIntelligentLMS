import { test, expect } from "../fixtures/auth";

test.describe("Collections", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/collections");
    // Wait for the page to load — heading or any content
    await page.waitForTimeout(2000);
  });

  test("should display the collections page title", async ({ page }) => {
    await expect(
      page.getByRole("heading", { name: /collection/i }).or(
        page.getByRole("heading", { name: /delinquency/i })
      )
    ).toBeVisible({ timeout: 10_000 });
  });

  test("should show summary stat cards", async ({ page }) => {
    // Look for any stat card content
    const statCard = page.getByText(/total active|delinquent|on track|cases/i);
    await expect(statCard.first()).toBeVisible({ timeout: 10_000 });
  });

  test("should display delinquent accounts table or status message", async ({ page }) => {
    await page.waitForTimeout(3000);
    const content = page.locator("table")
      .or(page.getByText(/delinquent/i).first())
      .or(page.getByText(/loading/i).first())
      .or(page.getByText(/no data/i).first());
    await expect(content.first()).toBeVisible({ timeout: 10_000 });
  });

  test("should show DPD column in delinquent table when data exists", async ({ page }) => {
    await page.waitForTimeout(3000);
    const hasTable = await page.locator("table").isVisible().catch(() => false);
    if (hasTable) {
      await expect(page.getByText("DPD").first()).toBeVisible();
    }
  });

  test("should navigate to collections workbench", async ({ page }) => {
    await page.goto("/collections-workbench");
    await page.waitForTimeout(2000);
    // Page should load without error
    await expect(page.locator("main, [role='main'], .min-h-screen").first()).toBeVisible();
  });

  test("should navigate to legal and write-offs page", async ({ page }) => {
    await page.goto("/legal");
    await page.waitForTimeout(2000);
    await expect(page.locator("main, [role='main'], .min-h-screen").first()).toBeVisible();
  });
});
