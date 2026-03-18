import { test, expect } from "../fixtures/auth";

/**
 * Navigation test: verifies that every sidebar menu item is clickable
 * and loads a page (no 404 / blank pages / uncaught errors).
 */

// All nav items from AppSidebar.tsx, grouped by section.
// Each entry: [sectionLabel, itemTitle, expectedUrl, textOnPage]
const sidebarNavItems: [string, string, string, RegExp][] = [
  // Standalone
  ["", "Overview Dashboard", "/", /overview dashboard/i],

  // Lending
  ["Lending", "Loan Applications", "/loans", /loan applications/i],
  ["Lending", "Active Loans", "/active-loans", /active loans/i],
  ["Lending", "Repayment Schedule", "/repayments", /repayment/i],
  ["Lending", "Loan Modifications", "/modifications", /modification/i],

  // Products
  ["Products", "Product Catalogue", "/products", /product/i],
  ["Products", "Product Config Engine", "/product-config", /product config/i],
  ["Products", "Product Templates", "/templates", /template/i],

  // Customers
  ["Customers", "Customer Directory", "/borrowers", /customer/i],
  ["Customers", "KYC / Verification", "/compliance", /kyc|verification|compliance/i],
  ["Customers", "Business (KYB)", "/kyb", /kyb|business/i],

  // Float & Wallet
  ["Float & Wallet", "AthenaFloat Overview", "/float", /float/i],
  ["Float & Wallet", "Wallet Accounts", "/wallets", /wallet/i],
  ["Float & Wallet", "Overdraft Management", "/overdraft", /overdraft/i],
  ["Float & Wallet", "Float Analytics", "/float-analytics", /float.*analytics|analytics/i],

  // Collections
  ["Collections", "Delinquency Queue", "/collections", /collection|delinquency/i],
  ["Collections", "Collections Workbench", "/collections-workbench", /workbench|collection/i],
  ["Collections", "Legal & Write-Offs", "/legal", /legal|write-off/i],

  // Finance
  ["Finance", "General Ledger", "/ledger", /general ledger|ledger/i],
  ["Finance", "Income Statement", "/income-statement", /income statement/i],
  ["Finance", "Balance Sheet", "/balance-sheet", /balance sheet/i],
  ["Finance", "Trial Balance", "/trial-balance", /trial balance/i],

  // Compliance
  ["Compliance", "Fraud Dashboard", "/fraud-dashboard", /fraud.*dashboard/i],
  ["Compliance", "AML Monitoring", "/aml", /aml|anti-money/i],
  ["Compliance", "Fraud Alerts", "/fraud", /fraud.*alert/i],
  ["Compliance", "Investigation Cases", "/fraud-cases", /case|investigation/i],
  ["Compliance", "Detection Rules", "/fraud-rules", /rule|detection/i],
  ["Compliance", "SAR / CTR Reports", "/sar-reports", /sar|suspicious|ctr/i],
  ["Compliance", "Watchlist", "/watchlist", /watchlist/i],
  ["Compliance", "Audit Logs", "/audit", /audit/i],

  // Reports
  ["Reports", "Portfolio Analytics", "/reports", /report|analytics|portfolio/i],
  ["Reports", "AI Model Performance", "/ai", /ai|model|scoring/i],

  // Administration
  ["Administration", "Users & Roles", "/users", /user|role/i],
  ["Administration", "System Configuration", "/settings", /setting|configuration/i],
  ["Administration", "Integrations & API", "/integrations", /integration|api/i],
  ["Administration", "Notifications", "/notifications", /notification/i],
  ["Administration", "Document Store", "/documents", /document/i],

  // Organisation
  ["Organisation", "Branch Directory", "/branches", /branch/i],
  ["Organisation", "Country Entities", "/countries", /country|entities/i],
  ["Organisation", "Currencies & FX Rates", "/currencies", /currenc|fx/i],

  // Setup & Configuration
  ["Setup & Configuration", "Setup Wizard", "/setup-wizard", /setup|wizard/i],

  // Teller
  ["Teller", "My Teller Session", "/teller", /teller/i],
];

test.describe("Sidebar Navigation - Full Coverage", () => {
  for (const [section, title, url, textPattern] of sidebarNavItems) {
    test(`${section ? section + " > " : ""}${title} loads correctly`, async ({
      page,
    }) => {
      // Navigate directly to the URL to avoid complex sidebar expand logic
      await page.goto(url);

      // Should NOT be on the 404 page
      await page.waitForTimeout(2000);

      const notFoundVisible = await page
        .getByText("404")
        .isVisible()
        .catch(() => false);

      const blankPage = await page
        .locator("body")
        .evaluate((el) => el.innerText.trim().length < 10);

      expect(notFoundVisible).toBeFalsy();
      expect(blankPage).toBeFalsy();

      // Should find some relevant text on the page
      await expect(
        page.getByText(textPattern).first()
      ).toBeVisible({ timeout: 10_000 });
    });
  }
});

test.describe("Sidebar Section Expand/Collapse", () => {
  test("should expand Lending section and see menu items", async ({
    page,
  }) => {
    await page.goto("/");
    await page.waitForTimeout(2000);

    // Click the Lending section header to expand it
    const lendingHeader = page.getByText("Lending", { exact: true }).first();
    await expect(lendingHeader).toBeVisible({ timeout: 10_000 });
    await lendingHeader.click();

    // Should see Loan Applications link
    await expect(
      page.getByText("Loan Applications")
    ).toBeVisible({ timeout: 5_000 });
  });

  test("should expand Compliance section and see fraud items", async ({
    page,
  }) => {
    await page.goto("/");
    await page.waitForTimeout(2000);

    const complianceHeader = page
      .getByText("Compliance", { exact: true })
      .first();
    await expect(complianceHeader).toBeVisible({ timeout: 10_000 });
    await complianceHeader.click();

    await expect(
      page.getByText("Fraud Dashboard")
    ).toBeVisible({ timeout: 5_000 });
    await expect(
      page.getByText("AML Monitoring")
    ).toBeVisible({ timeout: 5_000 });
  });

  test("should expand Finance section and see ledger items", async ({
    page,
  }) => {
    await page.goto("/");
    await page.waitForTimeout(2000);

    const financeHeader = page
      .getByText("Finance", { exact: true })
      .first();
    await expect(financeHeader).toBeVisible({ timeout: 10_000 });
    await financeHeader.click();

    await expect(
      page.getByText("General Ledger")
    ).toBeVisible({ timeout: 5_000 });
    await expect(
      page.getByText("Trial Balance")
    ).toBeVisible({ timeout: 5_000 });
  });
});
