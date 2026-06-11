/**
 * E2E tests for transfer display improvements in the Transactions page.
 * Covers:
 *   1. Filtering by account should include transfers where that account is the destination.
 *   2. Transfer description should show the user-entered text, not a generic fallback.
 *   3. Transfer row should show an "Account A → Account B" arrow instead of a single account name.
 *
 * Run with: make test-e2e
 *
 * Note: INCOME type filter intentionally does NOT include transfers — transfers are a
 * separate type and should only appear when filtering by TRANSFER.
 */
import { test, expect } from '@playwright/test';
import { registerAndLogin, testPassword, uniqueEmail } from './helpers';

test.describe('Transfer Display in Transactions Page', () => {
  const testPassword = 'password123';

  /** Register a fresh user and land on the Dashboard. */
  test.beforeEach(async ({ page }) => {
    const email = uniqueEmail('transfer-filter');
    await registerAndLogin(page, email, testPassword);
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

  // ─── Test 1: Account filter should include incoming transfers ─────────────────

  test('filtering by account should show transfers where it is the destination', async ({ page }) => {
    await createAccount(page, 'Source Account', '3000');
    await createAccount(page, 'Destination Account', '0');

    // Regular expense on Source Account
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'EXPENSE');
    await page.fill('input[id="trans-amount"]', '100');
    await page.fill('input[id="trans-desc"]', 'Groceries');
    await page.selectOption('select[id="trans-account"]', { label: 'Source Account' });
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // Transfer from Source → Destination
    await createTransfer(
      page,
      'Source Account',
      'Destination Account',
      '500',
      'Savings Transfer',
    );

    // Navigate to Transactions page
    await page.click('text=Transactions');
    await expect(page.getByRole('heading', { name: 'All Transactions' })).toBeVisible();

    // Verify both appear without any filter
    await expect(page.getByText('Groceries')).toBeVisible();
    await expect(page.getByText('Savings Transfer')).toBeVisible();

    // Filter by "Destination Account" — should show the transfer even though
    // Destination Account is the *to* account, not the *from* account.
    await page.selectOption('select#account-filter', { label: 'Destination Account' });

    // The incoming transfer should appear
    await expect(page.getByText('Savings Transfer')).toBeVisible();

    // Groceries belongs only to Source Account — should be hidden
    await expect(page.getByText('Groceries')).not.toBeVisible();
  });

  // ─── Test 2: Transfer shows actual description, not a generic fallback ─────────

  test('transfer with a description shows that description in the list', async ({ page }) => {
    await createAccount(page, 'My Wallet', '500');
    await createAccount(page, 'Savings Jar', '0');

    await createTransfer(page, 'My Wallet', 'Savings Jar', '100', 'Emergency Fund');

    await page.click('text=Transactions');
    await expect(page.getByRole('heading', { name: 'All Transactions' })).toBeVisible();

    // The user-entered description should appear — not "Transfer to Savings Jar" or plain "Transfer"
    await expect(page.getByText('Emergency Fund')).toBeVisible();
    await expect(page.getByText('Transfer to Savings Jar')).not.toBeVisible();
  });

  test('transfer without a description falls back to "Transfer" label', async ({ page }) => {
    await createAccount(page, 'Acc X', '200');
    await createAccount(page, 'Acc Y', '0');

    // Create transfer with no description
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'TRANSFER');
    await page.fill('input[id="trans-from-amount"]', '50');
    await page.fill('input[id="trans-adjustment"]', '0');
    // Intentionally leave description blank
    await page.selectOption('select[id="trans-account"]', { label: 'Acc X' });
    await page.selectOption('select[id="trans-to-account"]', { label: 'Acc Y' });
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    await page.click('text=Transactions');
    await expect(page.getByRole('heading', { name: 'All Transactions' })).toBeVisible();

    // Should fall back to "Transfer" — NOT "Transfer to Acc Y"
    // Scope to the transaction list rows (p.font-semibold) to avoid matching the <option> in the type select
    const txDescriptions = page.locator('p.font-semibold');
    await expect(txDescriptions.filter({ hasText: /^Transfer$/ })).toBeVisible();
    await expect(page.getByText('Transfer to Acc Y')).not.toBeVisible();
  });

  // ─── Test 3: Transfer row shows "Account A → Account B" arrow ───────────────

  test('transfer row shows arrow between from-account and to-account', async ({ page }) => {
    await createAccount(page, 'Arrow Source', '1000');
    await createAccount(page, 'Arrow Dest', '0');

    await createTransfer(page, 'Arrow Source', 'Arrow Dest', '300', 'Arrow Test Transfer');

    await page.click('text=Transactions');
    await expect(page.getByRole('heading', { name: 'All Transactions' })).toBeVisible();

    // The account sub-line should show "Arrow Source → Arrow Dest"
    await expect(page.getByText('Arrow Source → Arrow Dest')).toBeVisible();
  });
});
