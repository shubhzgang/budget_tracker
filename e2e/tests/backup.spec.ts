/**
 * Run these tests with:
 * make test-e2e
 */
import { test, expect } from '@playwright/test';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';
import { registerAndLogin, testPassword, uniqueEmail } from './helpers';

test.describe('Backup and Restore', () => {
  const getTestEmail = () => `backup-test-${Date.now()}-${Math.floor(Math.random() * 1000)}@example.com`;
  const testPassword = 'password123';
  let tempDir: string;

  test.beforeAll(() => {
    tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'playwright-backup-'));
  });

  test.afterAll(() => {
    if (fs.existsSync(tempDir)) {
      fs.rmSync(tempDir, { recursive: true, force: true });
    }
  });

  test.beforeEach(async ({ page }) => {
    // Auto-accept all confirmation dialogs
    page.on('dialog', dialog => dialog.accept());

    const email = uniqueEmail('backup');
    await registerAndLogin(page, email, testPassword);
  });

  async function navigateToBackup(page) {
    await page.goto('/settings');
    await page.click('button:has-text("Data & Backup")');
    await expect(page.getByRole('heading', { name: 'Data Management' })).toBeVisible();
  }

  test('should backup and restore SQL with data', async ({ page }) => {
    // 1. Create Data
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'SQL Backup Acc');
    await page.fill('input[id="initial-balance"]', '500');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
    
    // Wait for account to be visible in Layout context
    await expect(page.getByRole('heading', { name: 'SQL Backup Acc', exact: true })).toBeVisible();

    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '100');
    await page.fill('input[id="trans-desc"]', 'SQL Backup Test');
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // 2. Export SQL
    await navigateToBackup(page);
    await page.click('button:has-text("Export SQL (Full)")');
    await expect(page.getByText(/SQL export triggered successfully/)).toBeVisible();

    // 3. Download
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download' }).first().click();
    const download = await downloadPromise;
    const downloadPath = path.join(tempDir, download.suggestedFilename());
    await download.saveAs(downloadPath);

    // 4. Delete All Data
    await page.click('button:has-text("Delete All Data")');
    await expect(page.getByText('All data has been deleted.')).toBeVisible();

    // Verify UI is empty
    await page.goto('/');
    await expect(page.getByText('No accounts yet.')).toBeVisible();
    await expect(page.getByText('No transactions yet.')).toBeVisible();

    // 5. Restore
    await navigateToBackup(page);
    await page.setInputFiles('input#restore-upload', downloadPath);
    await expect(page.getByText('Data restored successfully. Please refresh the page.')).toBeVisible();

    // 6. Verify data
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'SQL Backup Acc', exact: true })).toBeVisible();
    await expect(page.getByText('SQL Backup Test', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('₹400.00').first()).toBeVisible(); // 500 - 100
  });

  test('should backup and restore CSV with data', async ({ page }) => {
    // 1. Create Data
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'CSV Backup Acc');
    await page.fill('input[id="initial-balance"]', '1000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
    
    // Wait for account to be visible in Layout context
    await expect(page.getByRole('heading', { name: 'CSV Backup Acc', exact: true })).toBeVisible();

    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '250');
    await page.fill('input[id="trans-desc"]', 'CSV Backup Test');
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // 2. Export CSV
    await navigateToBackup(page);
    await page.click('button:has-text("Export CSV (Transactions)")');
    await expect(page.getByText(/CSV export triggered successfully/)).toBeVisible();

    // 3. Download
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download' }).first().click();
    const download = await downloadPromise;
    const downloadPath = path.join(tempDir, download.suggestedFilename());
    await download.saveAs(downloadPath);

    // 4. Delete All Data
    await page.click('button:has-text("Delete All Data")');
    await expect(page.getByText('All data has been deleted.')).toBeVisible();

    // Verify UI is empty
    await page.goto('/');
    await expect(page.getByText('No accounts yet.')).toBeVisible();
    await expect(page.getByText('No transactions yet.')).toBeVisible();

    // 5. Restore
    await navigateToBackup(page);
    await page.setInputFiles('input#restore-upload', downloadPath);
    await expect(page.getByText('Data restored successfully. Please refresh the page.')).toBeVisible();

    // 6. Verify data (CSV restore appends, so we'll see it)
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'CSV Backup Acc', exact: true })).toBeVisible();
    await expect(page.getByText('CSV Backup Test', { exact: true }).first()).toBeVisible();
  });

  test('should handle export with no data', async ({ page }) => {
    await navigateToBackup(page);
    
    // SQL Export
    await page.click('button:has-text("Export SQL (Full)")');
    await expect(page.getByText(/SQL export triggered successfully/)).toBeVisible();

    // CSV Export
    await page.click('button:has-text("Export CSV (Transactions)")');
    await expect(page.getByText(/CSV export triggered successfully/)).toBeVisible();
    
    // Check history table (Header + 2 rows)
    const rows = page.locator('table tbody tr');
    await expect(rows).toHaveCount(2);
    
    await expect(page.getByText('SQL', { exact: true })).toBeVisible();
    await expect(page.getByText('CSV', { exact: true })).toBeVisible();
  });
});
