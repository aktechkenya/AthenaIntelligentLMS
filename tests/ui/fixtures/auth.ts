import { test as base, expect, Page } from "@playwright/test";

/**
 * Shared authentication fixture for Athena LMS UI tests.
 *
 * Most tests use the storageState from global-setup, so they start already
 * logged in. This fixture provides helpers for tests that need to log in/out
 * manually (e.g., auth.spec.ts).
 */
export interface AuthFixtures {
  loginPage: LoginPage;
}

export class LoginPage {
  constructor(private page: Page) {}

  async goto() {
    await this.page.goto("/login");
    await expect(this.page.locator("form")).toBeVisible({ timeout: 15_000 });
  }

  async login(email: string, password: string) {
    await this.page.locator("#email").fill(email);
    await this.page.locator("#password").fill(password);
    await this.page.getByRole("button", { name: /sign in/i }).click();
  }

  async expectLoginSuccess() {
    // Should navigate away from /login
    await this.page.waitForURL(/^(?!.*\/login)/, { timeout: 15_000 });
    await expect(
      this.page.getByText("Overview Dashboard").or(this.page.getByText("AthenaLMS"))
    ).toBeVisible({ timeout: 10_000 });
  }

  async expectLoginError(message?: string) {
    const errorLocator = this.page.locator(
      '[class*="destructive"], [role="alert"]'
    );
    await expect(errorLocator.first()).toBeVisible({ timeout: 10_000 });
    if (message) {
      await expect(errorLocator.first()).toContainText(message, {
        ignoreCase: true,
      });
    }
  }

  async logout() {
    // Click the logout button in the sidebar footer
    await this.page.getByTitle("Log out").click();
    await this.page.waitForURL(/\/login/, { timeout: 10_000 });
  }
}

/**
 * Extended test fixture that provides a fresh (non-authenticated) browser
 * context. Use this for login/logout tests that must NOT start authenticated.
 */
export const unauthenticatedTest = base.extend<AuthFixtures>({
  // Override storageState to empty so we start logged out
  storageState: async ({}, use) => {
    await use({ cookies: [], origins: [] } as any);
  },
  loginPage: async ({ page }, use) => {
    await use(new LoginPage(page));
  },
});

/**
 * Authenticated test — uses the default storageState (admin session).
 * Most test files import this.
 */
export const test = base.extend<AuthFixtures>({
  loginPage: async ({ page }, use) => {
    await use(new LoginPage(page));
  },
});

export { expect };
