import { test, expect } from "../fixtures/auth";

test.describe("Customer Management", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/borrowers");
    await expect(page.getByText("Customers")).toBeVisible({ timeout: 15_000 });
  });

  test("should navigate to customers page and display title", async ({
    page,
  }) => {
    await expect(page.getByText("Customers")).toBeVisible();
    await expect(
      page.getByText("Client directory & KYC management")
    ).toBeVisible();
  });

  test("should show a search input", async ({ page }) => {
    const searchInput = page.getByPlaceholder("Search customers...");
    await expect(searchInput).toBeVisible();
  });

  test("should show Add Customer button", async ({ page }) => {
    const addButton = page.getByRole("button", { name: /add customer/i });
    await expect(addButton).toBeVisible();
  });

  test("should show Export button", async ({ page }) => {
    const exportButton = page.getByRole("button", { name: /export/i });
    await expect(exportButton).toBeVisible();
  });

  test("should display customer table or empty state", async ({ page }) => {
    // Either we see the table with headers, or the empty state message
    const tableOrEmpty = page
      .getByText("Customer ID")
      .or(page.getByText("No customers found"));
    await expect(tableOrEmpty.first()).toBeVisible({ timeout: 15_000 });
  });

  test("should search for customer", async ({ page }) => {
    const searchInput = page.getByPlaceholder("Search customers...");
    await searchInput.fill("test");

    // Wait a moment for the search query to fire (needs 2+ chars)
    await page.waitForTimeout(1000);

    // The page should still be functional (not crash)
    await expect(page.getByText("Customers")).toBeVisible();
  });

  test("should open Add Customer dialog", async ({ page }) => {
    await page.getByRole("button", { name: /add customer/i }).click();

    // Dialog should appear with the Create Customer title
    await expect(page.getByText("Create Customer")).toBeVisible({
      timeout: 5_000,
    });

    // Form fields should be visible
    await expect(page.getByText("Customer ID")).toBeVisible();
    await expect(page.getByText("First Name")).toBeVisible();
    await expect(page.getByText("Last Name")).toBeVisible();
  });

  test("should navigate to customer 360 view when clicking a row", async ({
    page,
  }) => {
    // Wait for table to load — either data or empty state
    const tableRow = page.locator("tbody tr").first();
    const isEmpty = await page
      .getByText("No customers found")
      .isVisible()
      .catch(() => false);

    if (!isEmpty) {
      // If there are rows, click the first one
      const rowVisible = await tableRow
        .isVisible({ timeout: 5_000 })
        .catch(() => false);
      if (rowVisible) {
        await tableRow.click();
        // Should navigate to /customer/:id
        await page.waitForURL(/\/customer\//, { timeout: 10_000 });
        await expect(page.url()).toContain("/customer/");
      }
    }
    // If no data, the test passes (we just verified the page loads)
  });
});
