import { test, expect } from '@playwright/test';

test.describe('Zero Amount Validation', () => {
  // Use a helper to get unique emails for each test run
  const getTestEmail = () => `zero-test-${Date.now()}-${Math.floor(Math.random() * 1000)}@example.com`;
  const testPassword = 'password123';

  test.beforeEach(async ({ page }) => {
    const email = getTestEmail();
    // Register and Login to start fresh
    await page.goto('/register');
    await page.fill('input[placeholder="Email"]', email);
    await page.fill('input[placeholder="Password"]', testPassword);
    await page.click('button:has-text("Sign Up")');

    // Wait for redirect to Login page
    await expect(page).toHaveURL(/.*login/);
    await page.fill('input[placeholder="Email"]', email);
    await page.fill('input[placeholder="Password"]', testPassword);
    await page.click('button:has-text("Sign In")');
    await expect(page.getByRole('heading', { name: 'Accounts' })).toBeVisible();
  });

  test('should block zero amount and show alert', async ({ page }) => {
    // 1. Create a Bank Account first so we can add a transaction
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Test Account');
    await page.fill('input[id="initial-balance"]', '1000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();

    // 2. Open Add Transaction modal
    await page.click('button:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeVisible();

    // 3. Set amount to 0
    await page.fill('input[id="trans-amount"]', '0');

    // 4. Set up alert listener before clicking
    page.on('dialog', async dialog => {
      expect(dialog.message()).toBe('Amount must be greater than zero');
      await dialog.dismiss();
    });

    // 5. Try to submit
    await page.click('button[type="submit"]:has-text("Add Transaction")');

    // 6. Modal should still be open
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeVisible();
  });
});
