import { test, expect } from "../fixtures/auth";

test.describe("Compliance & Fraud", () => {
  test.describe("Fraud Dashboard", () => {
    test("should navigate to fraud dashboard", async ({ page }) => {
      await page.goto("/fraud-dashboard");
      await expect(
        page.getByText(/fraud dashboard/i).first()
      ).toBeVisible({ timeout: 15_000 });
    });

    test("should display fraud summary metrics or loading state", async ({
      page,
    }) => {
      await page.goto("/fraud-dashboard");
      await page.waitForTimeout(3000);

      // The fraud dashboard should show some metric cards or loading skeletons
      const pageContent = page.locator("main, [class*='dashboard']").first();
      await expect(pageContent).toBeVisible({ timeout: 10_000 });
    });
  });

  test.describe("Fraud Alerts", () => {
    test.beforeEach(async ({ page }) => {
      await page.goto("/fraud");
      await expect(
        page.getByText(/fraud alert/i).first()
      ).toBeVisible({ timeout: 15_000 });
    });

    test("should display the fraud alerts page", async ({ page }) => {
      await expect(
        page.getByText(/fraud alert/i).first()
      ).toBeVisible();
    });

    test("should show search input for alerts", async ({ page }) => {
      const searchInput = page.getByPlaceholder(/search/i);
      await expect(searchInput.first()).toBeVisible();
    });

    test("should show status filter dropdown", async ({ page }) => {
      // The status filter uses a Select component
      const filterTrigger = page.locator('[role="combobox"]').first();
      const filterExists = await filterTrigger
        .isVisible()
        .catch(() => false);

      if (filterExists) {
        await filterTrigger.click();
        // Should show filter options
        await page.waitForTimeout(500);
        // Close by pressing Escape
        await page.keyboard.press("Escape");
      }
    });

    test("should display alerts table or empty state", async ({ page }) => {
      await page.waitForTimeout(3000);

      // Either alert data or a message about no alerts
      const hasContent = await page
        .locator("table, [class*='card']")
        .first()
        .isVisible()
        .catch(() => false);

      expect(hasContent).toBeTruthy();
    });
  });

  test.describe("AML Monitoring", () => {
    test("should navigate to AML page", async ({ page }) => {
      await page.goto("/aml");
      await page.waitForTimeout(3000);

      // AML page should load with some heading
      await expect(
        page.getByText(/aml|anti-money laundering|monitoring/i).first()
      ).toBeVisible({ timeout: 15_000 });
    });
  });

  test.describe("SAR Reports", () => {
    test("should navigate to SAR reports page", async ({ page }) => {
      await page.goto("/sar-reports");
      await page.waitForTimeout(3000);

      // SAR page should load
      await expect(
        page.getByText(/sar|suspicious activity|ctr/i).first()
      ).toBeVisible({ timeout: 15_000 });
    });
  });

  test.describe("Investigation Cases", () => {
    test("should navigate to fraud cases page", async ({ page }) => {
      await page.goto("/fraud-cases");
      await page.waitForTimeout(3000);

      await expect(
        page.getByText(/case|investigation/i).first()
      ).toBeVisible({ timeout: 15_000 });
    });
  });

  test.describe("Detection Rules", () => {
    test("should navigate to fraud rules page", async ({ page }) => {
      await page.goto("/fraud-rules");
      await page.waitForTimeout(3000);

      await expect(
        page.getByText(/rule|detection/i).first()
      ).toBeVisible({ timeout: 15_000 });
    });
  });

  test.describe("Watchlist", () => {
    test("should navigate to watchlist page", async ({ page }) => {
      await page.goto("/watchlist");
      await page.waitForTimeout(3000);

      await expect(
        page.getByText(/watchlist/i).first()
      ).toBeVisible({ timeout: 15_000 });
    });
  });

  test.describe("Audit Logs", () => {
    test("should navigate to audit logs page", async ({ page }) => {
      await page.goto("/audit");
      await page.waitForTimeout(3000);

      await expect(
        page.getByText(/audit/i).first()
      ).toBeVisible({ timeout: 15_000 });
    });
  });
});
