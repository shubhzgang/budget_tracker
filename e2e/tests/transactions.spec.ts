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
    
    // Sync: wait for modal to close and card to appear
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
    const checkingsCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Checkings' }) });
    await expect(checkingsCard).toBeVisible();
    await expect(checkingsCard.getByText('$1,000.00')).toBeVisible();

    // 2. Add an Expense Transaction
    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '200');
    await page.fill('input[id="trans-desc"]', 'Shopping');
    await page.click('button[type="submit"]:has-text("Add Transaction")');

    // Sync: wait for modal to close
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

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
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
    const sourceCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Source Acc' }) });
    await expect(sourceCard).toBeVisible();

    // 2. Create Destination Account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Dest Acc');
    await page.fill('input[id="initial-balance"]', '0');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
    const destCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Dest Acc' }) });
    await expect(destCard).toBeVisible();

    // 3. Perform Transfer
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'TRANSFER');
    await page.fill('input[id="trans-amount"]', '150');
    await page.selectOption('select[id="trans-account"]', { label: 'Source Acc' });
    await page.selectOption('select[id="trans-to-account"]', { label: 'Dest Acc' });
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // 4. Verify Balances (Source: 500-150=350, Dest: 0+150=150)
    await expect(sourceCard.getByText('$350.00')).toBeVisible();
    await expect(destCard.getByText('$150.00')).toBeVisible();

    // 5. Verify Transaction List Visuals
    await expect(page.getByText('Transfer to Dest Acc')).toBeVisible();
    await expect(page.getByText('-$150.00')).toBeVisible();
    await expect(page.getByText('Transfer from Source Acc')).toBeVisible();
    await expect(page.getByText('+$150.00')).toBeVisible();
  });

  test('should allow transferring from Cash to Bank', async ({ page }) => {
    // 1. Create Cash Account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'My Cash');
    await page.selectOption('select[id="account-type"]', 'CASH');
    await page.fill('input[id="initial-balance"]', '200');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
    const cashCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'My Cash' }) });
    await expect(cashCard).toBeVisible();

    // 2. Create Bank Account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'My Bank');
    await page.selectOption('select[id="account-type"]', 'BANK');
    await page.fill('input[id="initial-balance"]', '500');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
    const bankCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'My Bank' }) });
    await expect(bankCard).toBeVisible();

    // 3. Perform Transfer (Cash -> Bank)
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'TRANSFER');
    await page.fill('input[id="trans-amount"]', '50');
    await page.selectOption('select[id="trans-account"]', { label: 'My Cash' });
    await page.selectOption('select[id="trans-to-account"]', { label: 'My Bank' });
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // 4. Verify Balances (Cash: 200-50=150, Bank: 500+50=550)
    await expect(cashCard.getByText('$150.00')).toBeVisible();
    await expect(bankCard.getByText('$550.00')).toBeVisible();

    // 5. Verify Transaction List
    await expect(page.getByText('Transfer to My Bank')).toBeVisible();
    await expect(page.getByText('Transfer from My Cash')).toBeVisible();
    await expect(page.getByText('+$50.00')).toBeVisible();
  });

  test('should correctly handle bank to credit card transfer (paying bill)', async ({ page }) => {
    // 1. Create Bank Account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Checkings');
    await page.fill('input[id="initial-balance"]', '1000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
    const bankCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Checkings' }) });
    await expect(bankCard).toBeVisible();

    // 2. Create Credit Card Account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Visa CC');
    await page.selectOption('select[id="account-type"]', 'CREDIT_CARD');
    await page.fill('input[id="initial-balance"]', '500'); // $500 debt
    await page.fill('input[id="credit-limit"]', '5000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
    const ccCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Visa CC' }) });
    await expect(ccCard).toBeVisible();

    // 3. Perform Transfer (Bank -> CC)
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'TRANSFER');
    await page.fill('input[id="trans-amount"]', '200');
    await page.selectOption('select[id="trans-account"]', { label: 'Checkings' });
    await page.selectOption('select[id="trans-to-account"]', { label: 'Visa CC' });
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // 4. Verify Balances
    // Bank: 1000 - 200 = 800
    // CC Debt: 500 - 200 = 300
    await expect(bankCard.getByText('$800.00')).toBeVisible();
    await expect(ccCard.getByText('Debt: $300.00')).toBeVisible();
  });

  test('should correctly handle credit card expenses (increase debt)', async ({ page }) => {
    // 1. Create Credit Card Account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Visa Credit');
    await page.selectOption('select[id="account-type"]', 'CREDIT_CARD');
    await page.fill('input[id="initial-balance"]', '450');
    await page.fill('input[id="credit-limit"]', '5000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();

    const visaCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Visa Credit' }) });
    await expect(visaCard).toBeVisible();
    await expect(visaCard.getByText('Debt: $450.00')).toBeVisible();
    await expect(visaCard.getByText('9.0%')).toBeVisible(); // 450/5000 = 9%

    // 2. Add Expense Transaction
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'EXPENSE');
    await page.fill('input[id="trans-amount"]', '250');
    await page.fill('input[id="trans-desc"]', 'Shopping');
    await page.selectOption('select[id="trans-account"]', { label: 'Visa Credit' });
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // 3. Verify Balance and Utilization INCREASED (450 + 250 = 700)
    await expect(visaCard.getByText('Debt: $700.00')).toBeVisible();
    await expect(visaCard.getByText('14.0%')).toBeVisible(); // 700/5000 = 14%
  });

  test('should display spending insights on the dashboard', async ({ page }) => {
    // 1. Create a Bank Account with initial balance
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Insight Account');
    await page.fill('input[id="initial-balance"]', '2000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();

    // 2. Add some expenses
    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '100');
    await page.fill('input[id="trans-desc"]', 'Insight Expense');
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // 3. Verify Analytics heading is visible
    await expect(page.getByRole('heading', { name: 'Spending Insights' })).toBeVisible();
    
    // 4. Verify that charts are rendered (indirectly by checking for chart-related elements if possible, 
    // but the heading itself confirms the section is present).
    // Given recharts might be hard to select in E2E without specific attributes, 
    // we just ensure the container exists.
    await expect(page.locator('div.grid.grid-cols-1.lg\\:grid-cols-2.gap-8')).toBeVisible();
  });

  test('should handle LEND and BORROW transactions correctly', async ({ page }) => {
    // 1. Create Friend Lending Account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Lending Account');
    await page.selectOption('select[id="account-type"]', 'FRIEND_LENDING');
    await page.fill('input[id="initial-balance"]', '0');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
    const lendingCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Lending Account' }) });

    // 2. Add BORROW Transaction (Getting money)
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'BORROW');
    await page.fill('input[id="trans-amount"]', '50');
    await page.fill('input[id="trans-desc"]', 'Borrow from Bob');
    await page.selectOption('select[id="trans-account"]', { label: 'Lending Account' });
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // Verify balance increases (Debt increases / they owe you decreases -> visually balance goes up)
    await expect(lendingCard.getByText('$50.00')).toBeVisible();
    await expect(lendingCard.getByText('They owe you')).toBeVisible();

    // 3. Add LEND Transaction (Giving money back or lending more)
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'LEND');
    await page.fill('input[id="trans-amount"]', '150'); // 50 - 150 = -100
    await page.fill('input[id="trans-desc"]', 'Lend more');
    await page.selectOption('select[id="trans-account"]', { label: 'Lending Account' });
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // Verify balance decreases
    await expect(lendingCard.getByText('-$100.00')).toBeVisible();
    await expect(lendingCard.getByText('You owe them')).toBeVisible();
  });
});
