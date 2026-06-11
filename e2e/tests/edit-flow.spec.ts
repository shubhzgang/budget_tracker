import { test, expect } from '@playwright/test';
import { registerAndLogin, testPassword, uniqueEmail } from './helpers';

test.describe('Editing Accounts and Transactions', () => {
  const testPassword = 'password123';

  test.beforeEach(async ({ page }) => {
    await registerAndLogin(page, uniqueEmail('edit'), testPassword);
  });

  test('should allow creating, editing, and updating an account and its transactions', async ({ page }) => {
    // 1. Create a bank account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'E2E Account');
    await page.fill('input[id="initial-balance"]', '1000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByText('E2E Account')).toBeVisible();

    // 2. Edit the account name
    await page.click('button[aria-label="Edit E2E Account"]');
    await expect(page.getByRole('heading', { name: 'Edit Account' })).toBeVisible();
    await page.fill('input[id="account-name"]', 'E2E Account Edited');
    await page.click('button[type="submit"]:has-text("Save Changes")');
    await expect(page.getByText('E2E Account Edited')).toBeVisible();
    await expect(page.getByText('E2E Account', { exact: true })).not.toBeVisible();

    // 3. Add a transaction
    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '100');
    await page.fill('input[id="trans-desc"]', 'Dinner with friends');
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByText('Dinner with friends')).toBeVisible();

    // Account balance should update to 900 (1000 - 100)
    const card = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'E2E Account Edited' }) });
    await expect(card.getByText('₹900.00')).toBeVisible();

    // 4. Edit the transaction
    await page.click('button[aria-label="Edit Dinner with friends"]');
    await expect(page.getByRole('heading', { name: 'Edit Transaction' })).toBeVisible();
    
    // Amount should be prefilled
    const amountInput = page.locator('input[id="trans-amount"]');
    await expect(amountInput).toHaveValue('100');
    
    // Change amount to 150
    await amountInput.fill('150');
    await page.click('button[type="submit"]:has-text("Save Changes")');

    // Wait for modal to disappear and list to update
    await expect(page.getByText('Dinner with friends')).toBeVisible();
    await expect(page.getByText('-₹150.00')).toBeVisible();

    // Account balance should update to 850 (1000 - 150)
    await expect(card.getByText('₹850.00')).toBeVisible();
  });
});
