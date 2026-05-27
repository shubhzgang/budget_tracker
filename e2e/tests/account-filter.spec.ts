/**
 * E2E tests for the Account Filter dropdown on the Transactions page.
 * Covers:
 *   1. Filter shows all transaction types (INCOME, EXPENSE, TRANSFER as source) for a selected account.
 *   2. Resetting to "All Accounts" restores the full unfiltered list.
 *   3. Account filter combined with type filter shows only matching transactions.
 *
 * Run with: make test-e2e
 */
import { test, expect } from '@playwright/test';

test.describe('Account Filter on Transactions Page', () => {
  const getTestEmail = () =>
    `account-filter-${Date.now()}-${Math.floor(Math.random() * 1000)}@example.com`;
  const testPassword = 'password123';

  /** Register a fresh user and land on the Dashboard. */
  test.beforeEach(async ({ page }) => {
    const email = getTestEmail();
    await page.goto('/register');
    await page.fill('input[placeholder="Email"]', email);
    await page.fill('input[placeholder="Password"]', testPassword);
    await page.click('button:has-text("Sign Up")');

    await expect(page).toHaveURL(/.*login/);
    await page.fill('input[placeholder="Email"]', email);
    await page.fill('input[placeholder="Password"]', testPassword);
    await page.click('button:has-text("Sign In")');
    await expect(page.getByRole('heading', { name: 'Accounts' })).toBeVisible();
  });

  // ─── Helpers ────────────────────────────────────────────────────────────────

  async function createAccount(page: any, name: string, balance: string, type = 'BANK') {
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', name);
    await page.selectOption('select[id="account-type"]', type);
    await page.fill('input[id="initial-balance"]', balance);
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
  }

  async function createTransaction(
    page: any,
    type: string,
    amount: string,
    description: string,
    account?: string,
  ) {
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', type);
    await page.fill('input[id="trans-amount"]', amount);
    await page.fill('input[id="trans-desc"]', description);
    if (account) {
      await page.selectOption('select[id="trans-account"]', { label: account });
    }
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();
  }

  async function createTransfer(
    page: any,
    fromAccount: string,
    toAccount: string,
    amount: string,
    description: string,
  ) {
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'TRANSFER');
    await page.fill('input[id="trans-from-amount"]', amount);
    await page.fill('input[id="trans-adjustment"]', '0');
    await page.fill('input[id="trans-desc"]', description);
    await page.selectOption('select[id="trans-account"]', { label: fromAccount });
    await page.selectOption('select[id="trans-to-account"]', { label: toAccount });
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();
  }

  // ─── Test 1: All transaction types visible when filtering by source account ──

  test('filter by account shows income, expense, and outgoing transfers for that account', async ({ page }) => {
    await createAccount(page, 'Primary Bank', '5000');
    await createAccount(page, 'Savings', '0');

    // Income on Primary Bank
    await createTransaction(page, 'INCOME', '1000', 'Salary', 'Primary Bank');
    // Expense on Primary Bank
    await createTransaction(page, 'EXPENSE', '200', 'Rent Payment', 'Primary Bank');
    // Transfer from Primary Bank → Savings
    await createTransfer(page, 'Primary Bank', 'Savings', '500', 'Monthly Savings');
    // Expense on Savings (should NOT appear when filtering by Primary Bank)
    await createTransaction(page, 'EXPENSE', '50', 'Savings Fee', 'Savings');

    await page.click('text=Transactions');
    await expect(page.getByRole('heading', { name: 'All Transactions' })).toBeVisible();

    // All 4 items visible without filter
    await expect(page.getByText('Salary')).toBeVisible();
    await expect(page.getByText('Rent Payment')).toBeVisible();
    await expect(page.getByText('Monthly Savings')).toBeVisible();
    await expect(page.getByText('Savings Fee')).toBeVisible();

    // Filter by Primary Bank
    await page.selectOption('select#account-filter', { label: 'Primary Bank' });

    // Primary Bank transactions should appear
    await expect(page.getByText('Salary')).toBeVisible();
    await expect(page.getByText('Rent Payment')).toBeVisible();
    await expect(page.getByText('Monthly Savings')).toBeVisible();

    // Savings Fee belongs only to Savings — should be gone
    await expect(page.getByText('Savings Fee')).not.toBeVisible();
  });

  // ─── Test 2: Clearing filter restores full list ───────────────────────────

  test('clearing account filter restores the full transaction list', async ({ page }) => {
    await createAccount(page, 'Account Alpha', '1000');
    await createAccount(page, 'Account Beta', '500');

    await createTransaction(page, 'EXPENSE', '100', 'Alpha Expense', 'Account Alpha');
    await createTransaction(page, 'EXPENSE', '80', 'Beta Expense', 'Account Beta');

    await page.click('text=Transactions');
    await expect(page.getByRole('heading', { name: 'All Transactions' })).toBeVisible();

    // Apply filter — only Alpha Expense visible
    await page.selectOption('select#account-filter', { label: 'Account Alpha' });
    await expect(page.getByText('Alpha Expense')).toBeVisible();
    await expect(page.getByText('Beta Expense')).not.toBeVisible();

    // Clear filter — both should reappear
    await page.selectOption('select#account-filter', { label: 'All Accounts' });
    await expect(page.getByText('Alpha Expense')).toBeVisible();
    await expect(page.getByText('Beta Expense')).toBeVisible();
  });

  // ─── Test 3: Account filter combined with type filter ────────────────────

  test('account filter combined with type filter shows only matching transactions', async ({ page }) => {
    await createAccount(page, 'Combo Account', '2000');
    await createAccount(page, 'Other Account', '500');

    await createTransaction(page, 'INCOME', '500', 'Combo Income', 'Combo Account');
    await createTransaction(page, 'EXPENSE', '150', 'Combo Expense', 'Combo Account');
    await createTransaction(page, 'EXPENSE', '75', 'Other Expense', 'Other Account');

    await page.click('text=Transactions');
    await expect(page.getByRole('heading', { name: 'All Transactions' })).toBeVisible();

    // Apply account filter
    await page.selectOption('select#account-filter', { label: 'Combo Account' });

    // Apply type filter
    await page.selectOption('select#type-filter', 'EXPENSE');

    // Only Combo Expense should match both filters
    await expect(page.getByText('Combo Expense')).toBeVisible();
    await expect(page.getByText('Combo Income')).not.toBeVisible();
    await expect(page.getByText('Other Expense')).not.toBeVisible();
  });
});
