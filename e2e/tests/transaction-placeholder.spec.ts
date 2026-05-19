import { test, expect } from '@playwright/test';

test.describe('Form Placeholders', () => {
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
  });

  test('should verify account form has 123 placeholder and is initially empty', async ({ page }) => {
    await page.click('button:has-text("Add Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeVisible();

    const balanceInput = page.locator('input[id="initial-balance"]');
    
    // Check placeholder
    await expect(balanceInput).toHaveAttribute('placeholder', '123');
    
    // Check initial value (should be empty now, not "0")
    expect(await balanceInput.inputValue()).toBe('');

    // Typing a number should work
    await balanceInput.fill('500');
    await expect(balanceInput).toHaveValue('500');

    await page.click('button:has-text("Cancel")');
  });

  test('should verify transaction form has 123 placeholder and is initially empty', async ({ page }) => {
    // First create an account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Test Account');
    await page.fill('input[id="initial-balance"]', '1000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();

    // Open transaction form
    await page.click('button[aria-label="Add Transaction"]');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeVisible();

    const amountInput = page.locator('input[id="trans-amount"]');

    // 1. Check if "123" is a placeholder
    await expect(amountInput).toHaveAttribute('placeholder', '123');
    
    // 2. Check if the initial value is empty
    expect(await amountInput.inputValue()).toBe('');

    // 3. Verify typing replaces placeholder
    await amountInput.fill('5');
    await expect(amountInput).toHaveValue('5');

    // 4. Compare with description placeholder
    const descInput = page.locator('input[id="trans-desc"]');
    await expect(descInput).toHaveAttribute('placeholder', 'What was this for?');
    expect(await descInput.inputValue()).toBe('');
  });
});
