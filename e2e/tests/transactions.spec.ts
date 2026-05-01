import { test, expect } from '@playwright/test';

test.describe('Transaction Management', () => {
  // Use a helper to get unique emails for each test run
  const getTestEmail = () => `trans-test-${Date.now()}-${Math.floor(Math.random() * 1000)}@example.com`;
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
    await expect(page.getByRole('heading', { name: 'Login' })).toBeVisible();

    await page.fill('input[placeholder="Email"]', email);
    await page.fill('input[placeholder="Password"]', testPassword);
    await page.click('button:has-text("Sign In")');
    await expect(page.getByRole('heading', { name: 'Accounts' })).toBeVisible();
  });

  test('should allow creating an account and adding a transaction', async ({ page }) => {
    // 1. Create a Bank Account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Checkings');
    await page.fill('input[id="initial-balance"]', '1000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    
    // Select the card specifically by heading name to be safe
    const checkingsCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Checkings' }) });
    await expect(checkingsCard).toBeVisible();
    await expect(checkingsCard.getByText('$1,000.00')).toBeVisible();

    // 2. Add an Expense Transaction
    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '200');
    await page.fill('input[id="trans-desc"]', 'Shopping');
    await page.click('button[type="submit"]:has-text("Add Transaction")');

    // 3. Verify Transaction in List
    await expect(page.getByText('Shopping')).toBeVisible();
    await expect(page.getByText('-$200.00')).toBeVisible();

    // 4. Verify Account Balance Update (1000 - 200 = 800)
    await expect(checkingsCard.getByText('$800.00')).toBeVisible();
  });

  test('should allow transferring money between accounts', async ({ page }) => {
    // 1. Create Source Account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Source Acc');
    await page.fill('input[id="initial-balance"]', '500');
    await page.click('button[type="submit"]:has-text("Create Account")');
    const sourceCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Source Acc' }) });

    // 2. Create Destination Account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Dest Acc');
    await page.fill('input[id="initial-balance"]', '0');
    await page.click('button[type="submit"]:has-text("Create Account")');
    const destCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Dest Acc' }) });

    // 3. Perform Transfer
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'TRANSFER');
    await page.fill('input[id="trans-amount"]', '150');
    await page.selectOption('select[id="trans-account"]', { label: 'Source Acc' });
    await page.selectOption('select[id="trans-to-account"]', { label: 'Dest Acc' });
    await page.click('button[type="submit"]:has-text("Add Transaction")');

    // 4. Verify Balances (Source: 500-150=350, Dest: 0+150=150)
    await expect(sourceCard.getByText('$350.00')).toBeVisible();
    await expect(destCard.getByText('$150.00')).toBeVisible();
  });

  test('should correctly handle credit card expenses (increase debt)', async ({ page }) => {
    // 1. Create Credit Card Account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Visa Credit');
    await page.selectOption('select[id="account-type"]', 'CREDIT_CARD');
    await page.fill('input[id="initial-balance"]', '450');
    await page.fill('input[id="credit-limit"]', '5000');
    await page.click('button[type="submit"]:has-text("Create Account")');

    const visaCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Visa Credit' }) });
    await expect(visaCard).toBeVisible();
    await expect(visaCard.getByText('$450.00')).toBeVisible();
    await expect(visaCard.getByText('9.0%')).toBeVisible(); // 450/5000 = 9%

    // 2. Add Expense Transaction
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'EXPENSE');
    await page.fill('input[id="trans-amount"]', '250');
    await page.fill('input[id="trans-desc"]', 'Shopping');
    await page.selectOption('select[id="trans-account"]', { label: 'Visa Credit' });
    await page.click('button[type="submit"]:has-text("Add Transaction")');

    // 3. Verify Balance and Utilization INCREASED (450 + 250 = 700)
    await expect(visaCard.getByText('$700.00')).toBeVisible();
    await expect(visaCard.getByText('14.0%')).toBeVisible(); // 700/5000 = 14%
  });
});
