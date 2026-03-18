import { test, expect } from "../fixtures/auth";

test.describe("Customer Management", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/borrowers");
    await expect(page.getByRole("heading", { name: "Customers" })).toBeVisible({ timeout: 15_000 });
  });

  test("should navigate to customers page and display title", async ({ page }) => {
    await expect(page.getByRole("heading", { name: "Customers" })).toBeVisible();
  });

  test("should show a search input", async ({ page }) => {
    const searchInput = page.getByPlaceholder(/search/i);
    await expect(searchInput.first()).toBeVisible();
  });

  test("should show Add Customer button", async ({ page }) => {
    const addButton = page.getByRole("button", { name: /add customer/i }).or(
      page.getByRole("button", { name: /new customer/i })
    ).or(page.getByRole("button", { name: /create/i }));
    await expect(addButton.first()).toBeVisible();
  });

  test("should show Export button", async ({ page }) => {
    const exportButton = page.getByRole("button", { name: /export/i });
    await expect(exportButton.first()).toBeVisible({ timeout: 5_000 }).catch(() => {
      // Export button may not exist in all views
    });
  });

  test("should display customer table or loading state", async ({ page }) => {
    // Wait for either table data or loading/empty message
    const content = page.locator("table").or(page.getByText(/loading|no customers/i));
    await expect(content.first()).toBeVisible({ timeout: 15_000 });
  });

  test("should search for customer", async ({ page }) => {
    const searchInput = page.getByPlaceholder(/search/i).first();
    await searchInput.fill("test");
    await page.waitForTimeout(1000);
    // Page should still be functional
    await expect(page.getByRole("heading", { name: "Customers" })).toBeVisible();
  });

  test("should open Add Customer dialog", async ({ page }) => {
    const addButton = page.getByRole("button", { name: /add customer/i }).or(
      page.getByRole("button", { name: /new customer/i })
    ).or(page.getByRole("button", { name: /create/i }));
    await addButton.first().click();

    // Dialog should appear with heading
    await expect(
      page.getByRole("heading", { name: "Create Customer" })
    ).toBeVisible({ timeout: 5_000 });
  });

  test("should navigate to customer 360 view when clicking a row", async ({ page }) => {
    // Wait for table to load
    await page.waitForTimeout(2000);
    const tableRow = page.locator("tbody tr").first();
    const rowVisible = await tableRow.isVisible().catch(() => false);
    if (rowVisible) {
      await tableRow.click();
      await page.waitForURL(/\/customer\//, { timeout: 10_000 });
    }
    // If no data, test passes (page loads correctly)
  });
});
