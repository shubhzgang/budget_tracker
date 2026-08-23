/**
 * E2E tests for Transaction and Transfer Update Bug Fixes.
 * Covers:
 *   1. Transaction update: Category and label preservation when editing amount/description.
 *   2. Transaction update: Account change updates old and new account balances correctly.
 *   3. Transaction update: Combination of fields (Account, Category, Label, Amount, Description).
 *   4. Transfer update: Category and label preservation when editing amount/date.
 *   5. Transfer update: Combination of fields (Amount, Description, Category, Label).
 *
 * Run with: make test-e2e
 */
import { test, expect } from '@playwright/test';
import { registerAndLogin, testPassword, uniqueEmail } from './helpers';

test.describe('Transaction and Transfer Update Bug Fixes', () => {
  test.beforeEach(async ({ page }) => {
    const email = uniqueEmail('update-fix');
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

  async function createCustomCategory(page: any, name: string) {
    await page.click('nav >> text=Settings');
    await page.click('button:has-text("Categories")');
    const nameInput = page.locator('form input[placeholder="e.g. Groceries"]');
    await nameInput.fill(name);
    await page.click('button[type="submit"]:has-text("Add")');
    await expect(page.getByText(name)).toBeVisible();
  }

  async function createCustomLabel(page: any, name: string) {
    await page.click('nav >> text=Settings');
    await page.click('button:has-text("Labels")');
    const nameInput = page.locator('form input[placeholder="e.g. Personal, Work, Tax-Deductible"]');
    await nameInput.fill(name);
    await page.click('button[type="submit"]:has-text("Add")');
    await expect(page.getByText(name)).toBeVisible();
  }

  // ─── Test 1: Transaction Update - Category & Label Preservation ──────────────

  test('transaction update: preserves category and label when editing only amount', async ({ page }) => {
    await createAccount(page, 'Checking', '1000');
    await createCustomCategory(page, 'Groceries');
    await createCustomLabel(page, 'Weekly');

    // Create Expense with Groceries category and Weekly label
    await page.click('nav >> text=Transactions');
    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '80');
    await page.fill('input[id="trans-desc"]', 'Grocery Shopping');
    await page.selectOption('select[id="trans-category"]', { label: '😀 Groceries' });

    // Select Weekly label
    const labelButton = page.getByTestId('label-select-toggle');
    await labelButton.click();
    await page.waitForTimeout(300);
    await page.getByTestId('label-dropdown').getByText('Weekly').click();
    await labelButton.click();

    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // Verify initial creation (Category icon 😀 and Weekly label visible)
    await expect(page.getByText('Grocery Shopping')).toBeVisible();
    await expect(page.getByText('😀')).toBeVisible();
    await expect(page.getByText('Weekly')).toBeVisible();

    // Edit transaction - change only the amount to 120
    await page.click('button[aria-label="Edit Grocery Shopping"]');
    await expect(page.getByRole('heading', { name: 'Edit Transaction' })).toBeVisible();

    await page.fill('input[id="trans-amount"]', '120');
    await page.click('button[type="submit"]:has-text("Save Changes")');

    // Assert updated amount and preserved category icon + label
    await expect(page.getByText('Grocery Shopping')).toBeVisible();
    await expect(page.getByText('-₹120.00')).toBeVisible();
    await expect(page.getByText('😀')).toBeVisible();
    await expect(page.getByText('Weekly')).toBeVisible();

    // Check account balance updated (1000 - 120 = 880)
    await page.click('nav >> text=Dashboard');
    const card = page.locator('div[data-testid="account-card"]', { has: page.getByRole('heading', { name: 'Checking' }) });
    await expect(card.getByText('₹880.00')).toBeVisible();
  });

  // ─── Test 2: Transaction Update - Account Change Balances ───────────────────

  test('transaction update: changing account reverts old balance and debits new balance', async ({ page }) => {
    await createAccount(page, 'Bank Alpha', '1000');
    await createAccount(page, 'Bank Beta', '500');

    // Create $200 expense on Bank Alpha
    await page.click('nav >> text=Transactions');
    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '200');
    await page.fill('input[id="trans-desc"]', 'Gadget Purchase');
    await page.selectOption('select[id="trans-account"]', { label: 'Bank Alpha' });
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // Verify initial balances (Alpha: 800, Beta: 500)
    await page.click('nav >> text=Dashboard');
    const cardAlpha = page.locator('div[data-testid="account-card"]', { has: page.getByRole('heading', { name: 'Bank Alpha' }) });
    const cardBeta = page.locator('div[data-testid="account-card"]', { has: page.getByRole('heading', { name: 'Bank Beta' }) });
    await expect(cardAlpha.getByText('₹800.00')).toBeVisible();
    await expect(cardBeta.getByText('₹500.00')).toBeVisible();

    // Edit transaction - move from Bank Alpha to Bank Beta
    await page.click('nav >> text=Transactions');
    await page.click('button[aria-label="Edit Gadget Purchase"]');
    await expect(page.getByRole('heading', { name: 'Edit Transaction' })).toBeVisible();

    await page.selectOption('select[id="trans-account"]', { label: 'Bank Beta' });
    await page.click('button[type="submit"]:has-text("Save Changes")');

    // Verify updated balances (Alpha: 1000 reverted, Beta: 300 debited)
    await page.click('nav >> text=Dashboard');
    await expect(cardAlpha.getByText('₹1,000.00')).toBeVisible();
    await expect(cardBeta.getByText('₹300.00')).toBeVisible();
  });

  // ─── Test 3: Transaction Update - Combination of Fields ───────────────────

  test('transaction update: combination update of account, category, label, amount, description', async ({ page }) => {
    await createAccount(page, 'Account X', '1000');
    await createAccount(page, 'Account Y', '500');
    await createCustomCategory(page, 'Utilities');
    await createCustomLabel(page, 'Monthly');

    // Create initial expense on Account X
    await page.click('nav >> text=Transactions');
    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '100');
    await page.fill('input[id="trans-desc"]', 'Electric Bill');
    await page.selectOption('select[id="trans-account"]', { label: 'Account X' });
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // Edit all fields: description, amount, account, category, label
    await page.click('button[aria-label="Edit Electric Bill"]');
    await expect(page.getByRole('heading', { name: 'Edit Transaction' })).toBeVisible();

    await page.fill('input[id="trans-desc"]', 'Power & Water Bill');
    await page.fill('input[id="trans-amount"]', '150');
    await page.selectOption('select[id="trans-account"]', { label: 'Account Y' });
    await page.selectOption('select[id="trans-category"]', { label: '😀 Utilities' });

    // Select Monthly label
    const labelButton = page.getByTestId('label-select-toggle');
    await labelButton.click();
    await page.waitForTimeout(300);
    await page.getByTestId('label-dropdown').getByText('Monthly').click();
    await labelButton.click();

    await page.click('button[type="submit"]:has-text("Save Changes")');

    // Assert UI updates
    await expect(page.getByText('Power & Water Bill')).toBeVisible();
    await expect(page.getByText('-₹150.00')).toBeVisible();
    await expect(page.getByText('😀')).toBeVisible();
    await expect(page.getByText('Monthly')).toBeVisible();

    // Verify balances (Account X: 1000, Account Y: 350)
    await page.click('nav >> text=Dashboard');
    const cardX = page.locator('div[data-testid="account-card"]', { has: page.getByRole('heading', { name: 'Account X' }) });
    const cardY = page.locator('div[data-testid="account-card"]', { has: page.getByRole('heading', { name: 'Account Y' }) });
    await expect(cardX.getByText('₹1,000.00')).toBeVisible();
    await expect(cardY.getByText('₹350.00')).toBeVisible();
  });

  // ─── Test 4: Transfer Update - Category & Label Preservation ───────────────

  test('transfer update: preserves category and label when editing amount', async ({ page }) => {
    await createAccount(page, 'Source Account', '1000');
    await createAccount(page, 'Target Account', '200');
    await createCustomCategory(page, 'Internal Transfer');
    await createCustomLabel(page, 'Savings Goal');

    // Create transfer with Category & Label
    await page.click('nav >> text=Transactions');
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'TRANSFER');
    await page.fill('input[id="trans-from-amount"]', '300');
    await page.fill('input[id="trans-adjustment"]', '0');
    await page.fill('input[id="trans-desc"]', 'Monthly Savings');
    await page.selectOption('select[id="trans-account"]', { label: 'Source Account' });
    await page.selectOption('select[id="trans-to-account"]', { label: 'Target Account' });
    await page.selectOption('select[id="trans-category"]', { label: '😀 Internal Transfer' });

    // Select Savings Goal label
    const labelButton = page.getByTestId('label-select-toggle');
    await labelButton.click();
    await page.waitForTimeout(300);
    await page.getByTestId('label-dropdown').getByText('Savings Goal').click();
    await labelButton.click();

    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // Verify initial transfer display
    await expect(page.getByText('Monthly Savings')).toBeVisible();
    await expect(page.getByText('😀')).toBeVisible();
    await expect(page.getByText('Savings Goal')).toBeVisible();

    // Edit transfer - change only the amount to 400
    await page.click('button[aria-label="Edit Monthly Savings"]');
    await expect(page.getByRole('heading', { name: 'Edit Transaction' })).toBeVisible();

    await page.fill('input[id="trans-from-amount"]', '400');
    await page.click('button[type="submit"]:has-text("Save Changes")');

    // Assert preserved category icon and label, and updated amount
    await expect(page.getByText('Monthly Savings')).toBeVisible();
    await expect(page.getByText('-₹400.00')).toBeVisible();
    await expect(page.getByText('😀')).toBeVisible();
    await expect(page.getByText('Savings Goal')).toBeVisible();

    // Verify balances (Source: 600, Target: 600)
    await page.click('nav >> text=Dashboard');
    const cardSrc = page.locator('div[data-testid="account-card"]', { has: page.getByRole('heading', { name: 'Source Account' }) });
    const cardTgt = page.locator('div[data-testid="account-card"]', { has: page.getByRole('heading', { name: 'Target Account' }) });
    await expect(cardSrc.getByText('₹600.00')).toBeVisible();
    await expect(cardTgt.getByText('₹600.00')).toBeVisible();
  });

  // ─── Test 5: Transfer Update - Combination of Fields ──────────────────────

  test('transfer update: combination update of amount, description, category, and label', async ({ page }) => {
    await createAccount(page, 'Acc 1', '1000');
    await createAccount(page, 'Acc 2', '100');
    await createCustomCategory(page, 'Investment');
    await createCustomLabel(page, 'Crypto');

    // Create simple transfer
    await page.click('nav >> text=Transactions');
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'TRANSFER');
    await page.fill('input[id="trans-from-amount"]', '200');
    await page.fill('input[id="trans-adjustment"]', '0');
    await page.fill('input[id="trans-desc"]', 'Deposit');
    await page.selectOption('select[id="trans-account"]', { label: 'Acc 1' });
    await page.selectOption('select[id="trans-to-account"]', { label: 'Acc 2' });
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // Edit transfer: description, amount, category, label
    await page.click('button[aria-label="Edit Deposit"]');
    await expect(page.getByRole('heading', { name: 'Edit Transaction' })).toBeVisible();

    await page.fill('input[id="trans-desc"]', 'Fund Investment');
    await page.fill('input[id="trans-from-amount"]', '300');
    await page.selectOption('select[id="trans-category"]', { label: '😀 Investment' });

    // Select Crypto label
    const labelButton = page.getByTestId('label-select-toggle');
    await labelButton.click();
    await page.waitForTimeout(300);
    await page.getByTestId('label-dropdown').getByText('Crypto').click();
    await labelButton.click();

    await page.click('button[type="submit"]:has-text("Save Changes")');

    // Assert updated UI fields
    await expect(page.getByText('Fund Investment')).toBeVisible();
    await expect(page.getByText('-₹300.00')).toBeVisible();
    await expect(page.getByText('😀')).toBeVisible();
    await expect(page.getByText('Crypto')).toBeVisible();

    // Verify balances (Acc 1: 700, Acc 2: 400)
    await page.click('nav >> text=Dashboard');
    const card1 = page.locator('div[data-testid="account-card"]', { has: page.getByRole('heading', { name: 'Acc 1' }) });
    const card2 = page.locator('div[data-testid="account-card"]', { has: page.getByRole('heading', { name: 'Acc 2' }) });
    await expect(card1.getByText('₹700.00')).toBeVisible();
    await expect(card2.getByText('₹400.00')).toBeVisible();
  });
});
