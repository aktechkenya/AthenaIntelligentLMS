import { unauthenticatedTest as test, expect } from "../fixtures/auth";

test.describe("Authentication", () => {
  test("should login with valid credentials and redirect to dashboard", async ({
    loginPage,
    page,
  }) => {
    await loginPage.goto();
    await loginPage.login("admin@athena.com", "admin123");
    await loginPage.expectLoginSuccess();

    // Verify we are on the dashboard
    await expect(page).toHaveURL("/");
    await expect(page.getByRole("heading", { name: "Overview Dashboard" })).toBeVisible();
  });

  test("should show error on invalid credentials", async ({
    loginPage,
    page,
  }) => {
    await loginPage.goto();
    await loginPage.login("bad@email.com", "wrongpassword");
    await loginPage.expectLoginError();

    // Should remain on the login page
    await expect(page).toHaveURL(/\/login/);
  });

  test("should show error with correct email but wrong password", async ({
    loginPage,
    page,
  }) => {
    await loginPage.goto();
    await loginPage.login("admin@athena.com", "wrongpassword");
    await loginPage.expectLoginError();
    await expect(page).toHaveURL(/\/login/);
  });

  test("should logout and redirect to login", async ({ loginPage, page }) => {
    // First, log in
    await loginPage.goto();
    await loginPage.login("admin@athena.com", "admin123");
    await loginPage.expectLoginSuccess();

    // Now log out
    await loginPage.logout();
    await expect(page).toHaveURL(/\/login/);

    // Verify we see the login form
    await expect(page.locator("form")).toBeVisible();
    await expect(page.getByText("Welcome back")).toBeVisible();
  });

  test("should persist session across page reload", async ({
    loginPage,
    page,
  }) => {
    // Log in
    await loginPage.goto();
    await loginPage.login("admin@athena.com", "admin123");
    await loginPage.expectLoginSuccess();

    // Reload the page
    await page.reload();

    // Should still be on the dashboard (not redirected to login)
    await page.waitForURL(/^(?!.*\/login)/, { timeout: 10_000 });
    await expect(
      page.getByRole("heading", { name: "Overview Dashboard" })
    ).toBeVisible({ timeout: 10_000 });
  });

  test("should redirect unauthenticated user to login", async ({ page }) => {
    // Try to access a protected page without logging in
    await page.goto("/loans");

    // Should redirect to login
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 });
  });

  test("should display demo credentials on login page", async ({
    loginPage,
    page,
  }) => {
    await loginPage.goto();
    await expect(page.getByText("Demo Credentials")).toBeVisible();
    await expect(page.getByText("admin@athena.com")).toBeVisible();
  });

  test("should prefill credentials when clicking demo user", async ({
    loginPage,
    page,
  }) => {
    await loginPage.goto();

    // Click the admin demo credential button
    await page.getByText("admin@athena.com").click();

    // Email field should be prefilled
    await expect(page.locator("#email")).toHaveValue("admin@athena.com");
    await expect(page.locator("#password")).toHaveValue("admin123");
  });
});
