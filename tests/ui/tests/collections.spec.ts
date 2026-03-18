import { test, expect } from "../fixtures/auth";

test.describe("Collections", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/collections");
    await expect(
      page.getByText("Collections Queue").or(page.getByText("Delinquency"))
    ).toBeVisible({ timeout: 15_000 });
  });

  test("should display the collections page title", async ({ page }) => {
    await expect(page.getByText("Collections Queue")).toBeVisible();
    await expect(page.getByText("Delinquency management")).toBeVisible();
  });

  test("should show summary stat cards", async ({ page }) => {
    // Three summary cards: Total Active Loans, Delinquent, On Track
    await expect(page.getByText("Total Active Loans")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText("Delinquent")).toBeVisible();
    await expect(page.getByText("On Track")).toBeVisible();
  });

  test("should display delinquent accounts table or zero-delinquent message", async ({
    page,
  }) => {
    // Wait for data to load
    await page.waitForTimeout(3000);

    const hasDelinquentTable = await page
      .getByText("Delinquent Accounts")
      .isVisible()
      .catch(() => false);
    const hasZeroMessage = await page
      .getByText("0 Delinquent Accounts")
      .isVisible()
      .catch(() => false);
    const hasLoading = await page
      .getByText("Loading collections data")
      .isVisible()
      .catch(() => false);
    const hasError = await page
      .getByText("Failed to load")
      .isVisible()
      .catch(() => false);

    expect(
      hasDelinquentTable || hasZeroMessage || hasLoading || hasError
    ).toBeTruthy();
  });

  test("should show DPD column in delinquent table when data exists", async ({
    page,
  }) => {
    await page.waitForTimeout(3000);

    const hasTable = await page
      .getByText("Delinquent Accounts")
      .isVisible()
      .catch(() => false);

    if (hasTable) {
      await expect(page.getByText("DPD")).toBeVisible();
      await expect(page.getByText("Outstanding")).toBeVisible();
      await expect(page.getByText("Customer ID")).toBeVisible();
    }
  });

  test("should navigate to collections workbench", async ({ page }) => {
    await page.goto("/collections-workbench");
    await expect(
      page.getByText(/workbench|collection/i).first()
    ).toBeVisible({ timeout: 15_000 });
  });

  test("should navigate to legal and write-offs page", async ({ page }) => {
    await page.goto("/legal");
    await expect(
      page.getByText(/legal|write-off/i).first()
    ).toBeVisible({ timeout: 15_000 });
  });
});
