import { test, expect } from '@playwright/test';

test.describe('Transaction Form Amount Input', () => {
  const testEmail = `test-${Date.now()}@example.com`;
  const testPassword = 'password123';

  test.beforeEach(async ({ page }) => {
    // 1. Register and Login
    await page.goto('/register');
    await page.fill('input[placeholder="Email"]', testEmail);
    await page.fill('input[placeholder="Password"]', testPassword);
    await page.click('button:has-text("Sign Up")');
    await expect(page).toHaveURL(/.*login/);
    await page.fill('input[placeholder="Email"]', testEmail);
    await page.fill('input[placeholder="Password"]', testPassword);
    await page.click('button:has-text("Sign In")');

    // 2. Create an account so we can open the transaction form
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Test Account');
    await page.fill('input[id="initial-balance"]', '1000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
  });

  test('should have 123 as placeholder and no leading zero', async ({ page }) => {
    // Open the transaction form
    await page.click('button:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeVisible();

    const amountInput = page.locator('input[id="trans-amount"]');

    // 1. Check if "123" is a placeholder, not a value
    await expect(amountInput).toHaveAttribute('placeholder', '123');
    
    // 2. Check if the initial value is empty (to show the placeholder)
    const initialValue = await amountInput.inputValue();
    expect(initialValue).toBe('');

    // 3. Verify that typing a number doesn't result in a leading zero (e.g., "05")
    await amountInput.fill('5');
    await expect(amountInput).toHaveValue('5');

    // 4. Verify that clearing the input returns it to empty (showing placeholder again)
    await amountInput.fill('');
    await expect(amountInput).toHaveValue('');
  });
});
