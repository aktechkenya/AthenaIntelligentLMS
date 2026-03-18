import { chromium } from "@playwright/test";
import path from "path";
import fs from "fs";

const STORAGE_STATE = path.join(__dirname, ".auth", "admin.json");

async function globalSetup() {
  const authDir = path.dirname(STORAGE_STATE);
  if (!fs.existsSync(authDir)) {
    fs.mkdirSync(authDir, { recursive: true });
  }

  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();

  // Navigate to login page and clear any existing session
  await page.goto("http://localhost:3001/login");
  await page.evaluate(() => localStorage.clear());
  await page.reload();

  // Wait for the login form
  await page.waitForSelector("#email", { timeout: 15_000 });

  // Fill in credentials
  await page.locator("#email").fill("admin@athena.com");
  await page.locator("#password").fill("admin123");

  // Click Sign In
  await page.getByRole("button", { name: /sign in/i }).click();

  // Wait for navigation away from login
  await page.waitForURL(/^(?!.*\/login)/, { timeout: 15_000 });

  // Save the authenticated storage state
  await context.storageState({ path: STORAGE_STATE });
  await browser.close();
}

export default globalSetup;
