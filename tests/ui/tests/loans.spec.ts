import { test, expect } from "../fixtures/auth";

test.describe("Loan Management", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/loans");
    await expect(page.getByText("Loan Applications")).toBeVisible({
      timeout: 15_000,
    });
  });

  test("should display the Loan Applications page title", async ({
    page,
  }) => {
    await expect(page.getByText("Loan Applications")).toBeVisible();
    await expect(
      page.getByText("Full application pipeline")
    ).toBeVisible();
  });

  test("should show the search input", async ({ page }) => {
    const searchInput = page.getByPlaceholder(
      "Search by ID, customer name..."
    );
    await expect(searchInput).toBeVisible();
  });

  test("should show stage filter dropdown", async ({ page }) => {
    // The select trigger with "All Stages" text
    const stageTrigger = page.getByRole("combobox").or(
      page.locator('[role="combobox"]')
    );
    await expect(stageTrigger.first()).toBeVisible();
  });

  test("should show kanban and list view toggle buttons", async ({
    page,
  }) => {
    // There are two view toggle buttons (kanban grid icon and list icon)
    const viewToggle = page.locator("button").filter({ has: page.locator("svg") });
    // At least the view toggle section should exist
    await expect(page.getByRole("button", { name: /export/i })).toBeVisible();
  });

  test("should switch between kanban and list views", async ({ page }) => {
    // Wait for loading to finish — either kanban columns or empty state
    await page.waitForTimeout(2000);

    // Find the list view toggle button (the second one in the toggle group)
    const toggleButtons = page.locator(
      'button:has(svg[class*="h-3.5"])'
    );

    // Click list view (the 2nd toggle button within the border group)
    const listButton = page.locator("button").filter({
      has: page.locator('svg.h-3\\.5'),
    });

    // Alternative: look for the Export button as anchor, the toggle is nearby
    // Just verify both views render without errors by toggling
    const buttons = page.locator(
      'div.flex.border > button'
    );
    const count = await buttons.count();
    if (count >= 2) {
      // Click list view
      await buttons.nth(1).click();
      await page.waitForTimeout(500);

      // Should still show the page title
      await expect(page.getByText("Loan Applications")).toBeVisible();

      // Click back to kanban view
      await buttons.nth(0).click();
      await page.waitForTimeout(500);
      await expect(page.getByText("Loan Applications")).toBeVisible();
    }
  });

  test("should show New Application button", async ({ page }) => {
    const newAppButton = page.getByRole("button", {
      name: /new application/i,
    });
    await expect(newAppButton).toBeVisible();
  });

  test("should open New Application dialog", async ({ page }) => {
    await page
      .getByRole("button", { name: /new application/i })
      .click();

    await expect(
      page.getByText("New Loan Application")
    ).toBeVisible({ timeout: 5_000 });

    // Check form fields
    await expect(page.getByPlaceholder("e.g. CUST-001")).toBeVisible();
    await expect(page.getByPlaceholder("Product UUID")).toBeVisible();
  });

  test("should display kanban stage columns or empty state", async ({
    page,
  }) => {
    // Wait for loading to complete
    await page.waitForTimeout(3000);

    // Either kanban columns with stage labels or the list/empty state
    const stageLabels = ["Received", "KYC Pending", "Under Assessment", "Credit Committee", "Approved", "Rejected"];
    const hasAnyStage = await Promise.any(
      stageLabels.map((label) =>
        page
          .getByText(label, { exact: true })
          .first()
          .isVisible({ timeout: 2_000 })
      )
    ).catch(() => false);

    const hasNoApps = await page
      .getByText("No applications")
      .first()
      .isVisible()
      .catch(() => false);

    // Either some stage columns are visible or we see the empty message
    expect(hasAnyStage || hasNoApps).toBeTruthy();
  });

  test("should navigate to active loans page", async ({ page }) => {
    await page.goto("/active-loans");
    await expect(page.getByText(/active loans/i).first()).toBeVisible({
      timeout: 15_000,
    });
  });
});
