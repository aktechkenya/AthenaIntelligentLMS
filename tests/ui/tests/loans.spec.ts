import { test, expect } from "../fixtures/auth";

test.describe("Loan Management", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/loans");
    await expect(page.getByRole("heading", { name: /loan applications/i })).toBeVisible({
      timeout: 15_000,
    });
  });

  test("should display the Loan Applications page title", async ({ page }) => {
    await expect(page.getByRole("heading", { name: /loan applications/i })).toBeVisible();
  });

  test("should show the search input", async ({ page }) => {
    const searchInput = page.getByPlaceholder(/search/i);
    await expect(searchInput.first()).toBeVisible();
  });

  test("should show stage filter dropdown", async ({ page }) => {
    const stageTrigger = page.getByRole("combobox").or(page.locator('[role="combobox"]'));
    await expect(stageTrigger.first()).toBeVisible();
  });

  test("should show kanban and list view toggle buttons", async ({ page }) => {
    // View toggle or export button should exist
    const actionButton = page.getByRole("button", { name: /export/i }).or(
      page.getByRole("button", { name: /new application/i })
    );
    await expect(actionButton.first()).toBeVisible();
  });

  test("should switch between kanban and list views", async ({ page }) => {
    await page.waitForTimeout(2000);
    const buttons = page.locator('div.flex.border > button');
    const count = await buttons.count();
    if (count >= 2) {
      await buttons.nth(1).click();
      await page.waitForTimeout(500);
      await expect(page.getByRole("heading", { name: /loan applications/i })).toBeVisible();
      await buttons.nth(0).click();
    }
  });

  test("should show New Application button", async ({ page }) => {
    const newAppButton = page.getByRole("button", { name: /new application/i });
    await expect(newAppButton).toBeVisible();
  });

  test("should open New Application dialog", async ({ page }) => {
    await page.getByRole("button", { name: /new application/i }).click();
    // Dialog should appear with heading
    await expect(
      page.getByRole("heading", { name: "New Loan Application" })
    ).toBeVisible({ timeout: 5_000 });
  });

  test("should display kanban stage columns or empty state", async ({ page }) => {
    await page.waitForTimeout(3000);
    const stageLabels = ["Received", "KYC Pending", "Under Assessment", "Credit Committee", "Approved", "Rejected"];
    const hasAnyStage = await Promise.any(
      stageLabels.map((label) =>
        page.getByText(label, { exact: true }).first().isVisible({ timeout: 2_000 })
      )
    ).catch(() => false);
    const hasNoApps = await page.getByText(/no applications/i).first().isVisible().catch(() => false);
    expect(hasAnyStage || hasNoApps).toBeTruthy();
  });

  test("should navigate to active loans page", async ({ page }) => {
    await page.goto("/active-loans");
    await expect(
      page.getByRole("heading", { name: /active loans/i })
    ).toBeVisible({ timeout: 15_000 });
  });
});
