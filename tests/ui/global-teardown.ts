import { test as teardown } from "@playwright/test";
import fs from "fs";
import path from "path";

const STORAGE_STATE = path.join(__dirname, ".auth", "admin.json");

teardown("cleanup auth state", async () => {
  // Optionally remove stored auth state after test runs
  if (fs.existsSync(STORAGE_STATE)) {
    fs.unlinkSync(STORAGE_STATE);
  }
});
