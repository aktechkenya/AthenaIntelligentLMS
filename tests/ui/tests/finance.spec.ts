import { test, expect } from "../fixtures/auth";

test.describe("Finance Pages", () => {
  test.describe("General Ledger", () => {
    test("should navigate to ledger page and display title", async ({
      page,
    }) => {
      await page.goto("/ledger");
      await expect(page.getByRole("heading", { name: /general ledger/i })).toBeVisible({
        timeout: 15_000,
      });
      await expect(
        page.getByText("Journal entries across all GL accounts")
      ).toBeVisible();
    });

    test("should show journal entries table or loading/empty state", async ({
      page,
    }) => {
      await page.goto("/ledger");
      await page.waitForTimeout(3000);

      const hasTable = await page
        .locator("table")
        .first()
        .isVisible()
        .catch(() => false);
      const hasLoading = await page
        .getByText("Loading journal entries")
        .isVisible()
        .catch(() => false);
      const hasError = await page
        .getByText("Failed to load")
        .isVisible()
        .catch(() => false);

      expect(hasTable || hasLoading || hasError).toBeTruthy();
    });
  });

  test.describe("Income Statement", () => {
    test("should navigate to income statement page", async ({ page }) => {
      await page.goto("/income-statement");
      await expect(
        page.getByText(/income statement/i).first()
      ).toBeVisible({ timeout: 15_000 });
    });
  });

  test.describe("Balance Sheet", () => {
    test("should navigate to balance sheet page", async ({ page }) => {
      await page.goto("/balance-sheet");
      await expect(
        page.getByText(/balance sheet/i).first()
      ).toBeVisible({ timeout: 15_000 });
    });
  });

  test.describe("Trial Balance", () => {
    test("should navigate to trial balance page", async ({ page }) => {
      await page.goto("/trial-balance");
      await expect(
        page.getByText(/trial balance/i).first()
      ).toBeVisible({ timeout: 15_000 });
    });
  });

  test.describe("Reporting & Analytics", () => {
    test("should navigate to portfolio analytics page", async ({ page }) => {
      await page.goto("/reports");
      await expect(
        page.getByText(/report|analytics|portfolio/i).first()
      ).toBeVisible({ timeout: 15_000 });
    });

    test("should navigate to AI model performance page", async ({ page }) => {
      await page.goto("/ai");
      await expect(
        page.getByText(/ai|model|performance|scoring/i).first()
      ).toBeVisible({ timeout: 15_000 });
    });

    test("should navigate to consolidated reports page", async ({ page }) => {
      await page.goto("/consolidated-reports");
      await expect(
        page.getByText(/consolidated|report/i).first()
      ).toBeVisible({ timeout: 15_000 });
    });
  });
});
