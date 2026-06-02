/**
 * Run these tests with:
 * make test-e2e
 */
import { test, expect } from '@playwright/test';

test.describe('User Preferences', () => {
  const password = 'password123';
  let email = '';

  test.beforeEach(async ({ page }) => {
    email = `pref_e2e_${Math.floor(Math.random() * 100000)}_${Date.now()}@example.com`;
    // 1. Register
    await page.goto('/register');
    await page.fill('input[placeholder="Email"]', email);
    await page.fill('input[placeholder="Password"]', password);
    await page.click('button:has-text("Sign Up")');
    
    // 2. Login
    await expect(page).toHaveURL(/.*login/);
    await page.fill('input[placeholder="Email"]', email);
    await page.fill('input[placeholder="Password"]', password);
    await page.click('button:has-text("Sign In")');
    await expect(page).toHaveURL(/.*dashboard/);
  });

  test('should set and apply default transaction values', async ({ page }) => {
    // 1. Create an account first
    await page.click('button:has-text("Add Account")');
    await page.fill('#account-name', 'Primary Bank');
    await page.fill('#initial-balance', '1000');
    await page.click('button:has-text("Create Account")');
    await expect(page.locator('text=Primary Bank')).toBeVisible();

    // 2. Go to Settings and set defaults
    await page.click('nav >> text=Settings');
    await page.click('button:has-text("Defaults")');

    await page.selectOption('#pref-account', { label: 'Primary Bank' });
    await page.selectOption('#pref-type', { label: 'Income' });

    await page.click('button:has-text("Save Preferences")');
    // Wait for the button to be enabled again (post-save)
    await expect(page.locator('button:has-text("Save Preferences")')).toBeEnabled();

    // 3. Go to Transactions and verify defaults in the form
    await page.click('nav >> text=Transactions');
    await page.click('button:has-text("Add Transaction")');

    // Verify pre-populated values
    const typeValue = await page.inputValue('#trans-type');
    expect(typeValue).toBe('INCOME');

    const accountText = await page.locator('#trans-account >> option:checked').textContent();
    expect(accountText?.trim()).toBe('Primary Bank');
  });

  test('should set and apply default label with multi-select', async ({ page }) => {
    // 1. Create an account first
    await page.click('button:has-text("Add Account")');
    await page.fill('#account-name', 'Label Bank');
    await page.fill('#initial-balance', '500');
    await page.click('button:has-text("Create Account")');
    await expect(page.locator('text=Label Bank')).toBeVisible();

    // 2. Go to Settings > Defaults and set default label to WANTS
    await page.click('nav >> text=Settings');
    await page.click('button:has-text("Defaults")');

    await page.selectOption('#pref-label', { label: 'WANTS' });

    await page.click('button:has-text("Save Preferences")');
    await expect(page.locator('button:has-text("Save Preferences")')).toBeEnabled();

    // 3. Go to Transactions and verify the multi-select shows WANTS pre-selected
    await page.click('nav >> text=Transactions');
    await page.click('button:has-text("Add Transaction")');

    // The multi-select button should display the WANTS badge
    const labelButton = page.getByTestId('label-select-toggle');
    await expect(labelButton).toContainText('WANTS');
  });
});
