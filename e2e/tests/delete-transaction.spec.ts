/**
 * E2E tests for deleting transactions and transfers.
 * Run these tests with: make test-e2e
 */
import { test, expect } from '@playwright/test';

test.describe('Delete Transaction', () => {
  const getTestEmail = () => `del-test-${Date.now()}-${Math.floor(Math.random() * 1000)}@example.com`;
  const testPassword = 'password123';

  test.beforeEach(async ({ page }) => {
    const email = getTestEmail();
    // Register and Login
    await page.goto('/register');
    await page.fill('input[placeholder="Email"]', email);
    await page.fill('input[placeholder="Password"]', testPassword);
    await page.click('button:has-text("Sign Up")');

    await expect(page).toHaveURL(/.*login/);
    await expect(page.getByRole('heading', { name: 'Login' })).toBeVisible();

    await page.fill('input[placeholder="Email"]', email);
    await page.fill('input[placeholder="Password"]', testPassword);
    await page.click('button:has-text("Sign In")');
    await expect(page.getByRole('heading', { name: 'Accounts' })).toBeVisible();
  });

  test('should delete an expense transaction and revert account balance', async ({ page }) => {
    // 1. Create a Bank Account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Delete Test Bank');
    await page.fill('input[id="initial-balance"]', '1000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
    const bankCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Delete Test Bank' }) });
    await expect(bankCard.getByText('₹1,000.00')).toBeVisible();

    // 2. Add an Expense Transaction
    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '300');
    await page.fill('input[id="trans-desc"]', 'Expense to delete');
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // Verify balance went down (1000 - 300 = 700)
    await expect(bankCard.getByText('₹700.00')).toBeVisible();
    await expect(page.getByText('Expense to delete')).toBeVisible();

    // 3. Click the delete button on the transaction row
    const transactionRow = page.locator('div.group', { hasText: 'Expense to delete' });
    await transactionRow.getByTitle('Delete').click();

    // 4. Confirm deletion in the modal
    await expect(page.getByRole('heading', { name: 'Delete Transaction' })).toBeVisible();
    await expect(page.getByText(/Are you sure you want to delete/)).toBeVisible();
    await page.click('button:has-text("Delete"):not(:has-text("Deleting"))');
    await expect(page.getByRole('heading', { name: 'Delete Transaction' })).not.toBeVisible();

    // 5. Verify the transaction is gone
    await expect(page.getByText('Expense to delete', { exact: true })).not.toBeVisible();

    // 6. Verify balance is restored (back to 1000)
    await expect(bankCard.getByText('₹1,000.00')).toBeVisible();

    // 7. Verify success toast
    await expect(page.getByText('Transaction deleted successfully')).toBeVisible();
  });

  test('should delete an income transaction and revert account balance', async ({ page }) => {
    // 1. Create a Bank Account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Income Bank');
    await page.fill('input[id="initial-balance"]', '500');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
    const bankCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Income Bank' }) });
    await expect(bankCard.getByText('₹500.00')).toBeVisible();

    // 2. Add an Income Transaction
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'INCOME');
    await page.fill('input[id="trans-amount"]', '200');
    await page.fill('input[id="trans-desc"]', 'Salary to delete');
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // Verify balance went up (500 + 200 = 700)
    await expect(bankCard.getByText('₹700.00')).toBeVisible();

    // 3. Delete the transaction
    const transactionRow = page.locator('div.group', { hasText: 'Salary to delete' });
    await transactionRow.getByTitle('Delete').click();
    await expect(page.getByRole('heading', { name: 'Delete Transaction' })).toBeVisible();
    await page.click('button:has-text("Delete"):not(:has-text("Deleting"))');
    await expect(page.getByRole('heading', { name: 'Delete Transaction' })).not.toBeVisible();

    // 4. Verify transaction is gone and balance is reverted (back to 500)
    await expect(page.getByText('Salary to delete', { exact: true })).not.toBeVisible();
    await expect(bankCard.getByText('₹500.00')).toBeVisible();
  });

  test('should delete a transfer and revert both account balances', async ({ page }) => {
    // 1. Create Source Account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Transfer Source');
    await page.fill('input[id="initial-balance"]', '800');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
    const sourceCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Transfer Source' }) });
    await expect(sourceCard.getByText('₹800.00')).toBeVisible();

    // 2. Create Destination Account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Transfer Dest');
    await page.fill('input[id="initial-balance"]', '100');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
    const destCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Transfer Dest' }) });
    await expect(destCard.getByText('₹100.00')).toBeVisible();

    // 3. Create a Transfer
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'TRANSFER');
    await page.fill('input[id="trans-from-amount"]', '250');
    await page.fill('input[id="trans-adjustment"]', '0');
    await page.fill('input[id="trans-desc"]', 'Transfer to delete');
    await page.selectOption('select[id="trans-account"]', { label: 'Transfer Source' });
    await page.selectOption('select[id="trans-to-account"]', { label: 'Transfer Dest' });
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // Verify balances after transfer (Source: 800-250=550, Dest: 100+250=350)
    await expect(sourceCard.getByText('₹550.00')).toBeVisible();
    await expect(destCard.getByText('₹350.00')).toBeVisible();
    await expect(page.getByText('Transfer to delete')).toBeVisible();

    // 4. Delete the transfer
    const transactionRow = page.locator('div.group', { hasText: 'Transfer to delete' });
    await transactionRow.getByTitle('Delete').click();
    await expect(page.getByRole('heading', { name: 'Delete Transaction' })).toBeVisible();
    await page.click('button:has-text("Delete"):not(:has-text("Deleting"))');
    await expect(page.getByRole('heading', { name: 'Delete Transaction' })).not.toBeVisible();

    // 5. Verify the transfer is gone
    await expect(page.getByText('Transfer to delete', { exact: true })).not.toBeVisible();

    // 6. Verify both balances are restored (Source: 800, Dest: 100)
    await expect(sourceCard.getByText('₹800.00')).toBeVisible();
    await expect(destCard.getByText('₹100.00')).toBeVisible();
  });

  test('should cancel deletion when cancel is clicked in confirmation dialog', async ({ page }) => {
    // 1. Create Account and Transaction
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Cancel Test');
    await page.fill('input[id="initial-balance"]', '500');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();

    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '100');
    await page.fill('input[id="trans-desc"]', 'Do not delete me');
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();
    await expect(page.getByText('Do not delete me')).toBeVisible();

    // 2. Click delete, then cancel
    const transactionRow = page.locator('div.group', { hasText: 'Do not delete me' });
    await transactionRow.getByTitle('Delete').click();
    await expect(page.getByRole('heading', { name: 'Delete Transaction' })).toBeVisible();
    await page.click('button:has-text("Cancel")');

    // 3. Verify the dialog is closed and transaction is still there
    await expect(page.getByRole('heading', { name: 'Delete Transaction' })).not.toBeVisible();
    await expect(page.getByText('Do not delete me')).toBeVisible();
  });

  test('should delete a transaction from the Transactions page', async ({ page }) => {
    // 1. Create Account and Transaction
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Trans Page Bank');
    await page.fill('input[id="initial-balance"]', '600');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();

    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '75');
    await page.fill('input[id="trans-desc"]', 'Delete from list page');
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // 2. Navigate to Transactions page
    await page.click('text=Transactions');
    await expect(page.getByRole('heading', { name: 'All Transactions' })).toBeVisible();
    await expect(page.getByText('Delete from list page')).toBeVisible();

    // 3. Delete from the Transactions page
    const transactionRow = page.locator('div.group', { hasText: 'Delete from list page' });
    await transactionRow.getByTitle('Delete').click();
    await expect(page.getByRole('heading', { name: 'Delete Transaction' })).toBeVisible();
    await page.click('button:has-text("Delete"):not(:has-text("Deleting"))');
    await expect(page.getByRole('heading', { name: 'Delete Transaction' })).not.toBeVisible();

    // 4. Verify it's gone from the Transactions page
    await expect(page.getByText('Delete from list page', { exact: true })).not.toBeVisible();
    await expect(page.getByText('Transaction deleted successfully')).toBeVisible();

    // 5. Navigate back to Dashboard and verify balance is restored (600)
    await page.click('text=Dashboard');
    const bankCard = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Trans Page Bank' }) });
    await expect(bankCard.getByText('₹600.00')).toBeVisible();
  });
});
