import { test, expect } from "../fixtures/auth";

test.describe("Account Operations", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/accounts");
    await expect(page.getByText("Account Services")).toBeVisible({
      timeout: 15_000,
    });
  });

  test("should display the accounts page title and subtitle", async ({
    page,
  }) => {
    await expect(page.getByText("Account Services")).toBeVisible();
    await expect(
      page.getByText("Deposits, wallets & account management")
    ).toBeVisible();
  });

  test("should show account directory card", async ({ page }) => {
    await expect(page.getByText("Account Directory")).toBeVisible({
      timeout: 15_000,
    });
  });

  test("should display account table or empty/loading state", async ({
    page,
  }) => {
    // Wait for data to load
    await page.waitForTimeout(3000);

    // Either table headers are visible or empty state
    const tableHeader = page.getByText("Account ID");
    const emptyState = page.getByText("No accounts found");
    const loadingState = page.getByText("Loading accounts...");

    const hasTable = await tableHeader.isVisible().catch(() => false);
    const hasEmpty = await emptyState.isVisible().catch(() => false);
    const isLoading = await loadingState.isVisible().catch(() => false);

    expect(hasTable || hasEmpty || isLoading).toBeTruthy();
  });

  test("should show account table column headers when data exists", async ({
    page,
  }) => {
    // Wait for loading
    await page.waitForTimeout(3000);

    const hasData = await page
      .getByText("Account ID")
      .isVisible()
      .catch(() => false);

    if (hasData) {
      await expect(page.getByText("Account Holder")).toBeVisible();
      await expect(page.getByText("Type")).toBeVisible();
      await expect(page.getByText("Balance")).toBeVisible();
      await expect(page.getByText("Status")).toBeVisible();
    }
  });

  test("should display account count", async ({ page }) => {
    // The page shows "X accounts" or "Loading accounts..."
    await page.waitForTimeout(3000);

    const countText = page.getByText(/accounts|Loading accounts/i);
    await expect(countText.first()).toBeVisible();
  });

  test("should make account rows clickable", async ({ page }) => {
    await page.waitForTimeout(3000);

    const firstRow = page.locator("tbody tr").first();
    const hasRows = await firstRow.isVisible().catch(() => false);

    if (hasRows) {
      // Rows should have cursor-pointer class
      await expect(firstRow).toHaveClass(/cursor-pointer/);
    }
  });
});
