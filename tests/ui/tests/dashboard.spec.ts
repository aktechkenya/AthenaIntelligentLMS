import { test, expect } from "../fixtures/auth";

test.describe("Dashboard", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/");
    // Wait for the dashboard to be visible
    await expect(page.getByText("Overview Dashboard")).toBeVisible({
      timeout: 15_000,
    });
  });

  test("should load with the overview title and subtitle", async ({
    page,
  }) => {
    await expect(page.getByText("Overview Dashboard")).toBeVisible();
    await expect(
      page.getByText("Real-time portfolio overview & key metrics")
    ).toBeVisible();
  });

  test("should display KPI metric cards", async ({ page }) => {
    // Check that the five KPI cards are present
    const kpiTitles = [
      "Active Loans",
      "Loan Book (KES)",
      "Outstanding (KES)",
      "Collected (KES)",
      "PAR30 (%)",
    ];

    for (const title of kpiTitles) {
      await expect(page.getByText(title, { exact: false })).toBeVisible();
    }
  });

  test("should show the sidebar with AthenaLMS branding", async ({
    page,
  }) => {
    await expect(page.getByText("AthenaLMS")).toBeVisible();
    await expect(page.getByText("Lending Platform")).toBeVisible();
  });

  test("should show user info in sidebar footer", async ({ page }) => {
    // The sidebar footer should show the logged-in user's name
    // Admin user is "John Mwangi" or "Admin User" depending on backend/demo
    const userNameLocator = page
      .locator("aside, [data-sidebar]")
      .getByText(/mwangi|admin/i);
    await expect(userNameLocator.first()).toBeVisible({ timeout: 10_000 });
  });

  test("should show the analytics placeholder section", async ({ page }) => {
    await expect(page.getByText("Analytics charts")).toBeVisible();
  });

  test("should have a visible Overview Dashboard link in the sidebar", async ({
    page,
  }) => {
    const dashboardLink = page.getByRole("link", {
      name: /overview dashboard/i,
    });
    await expect(dashboardLink).toBeVisible();
  });
});
