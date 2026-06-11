import { test, expect } from '@playwright/test';
import { registerAndLogin, testPassword, uniqueEmail } from './helpers';

test.describe('Zero Amount Validation', () => {
  const testPassword = 'password123';

  test.beforeEach(async ({ page }) => {
    const email = uniqueEmail('zero');
    await registerAndLogin(page, email, testPassword);
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
