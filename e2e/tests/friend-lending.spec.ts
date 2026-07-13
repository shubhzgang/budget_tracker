/**
 * E2E tests for Friend/Lending accounts and net worth calculation.
 * Covers:
 *   1. "They owe me" direction → positive balance → increases net worth.
 *   2. "I owe them" direction → negative balance → decreases net worth.
 *   3. Combined net worth across friend accounts is correct.
 *
 * Run with: make test-e2e
 */
import { test, expect } from '@playwright/test';
import { registerAndLogin, testPassword, uniqueEmail } from './helpers';

test.describe('Friend/Lending Accounts & Net Worth', () => {
  test.beforeEach(async ({ page }) => {
    const email = uniqueEmail('friend-lending');
    await registerAndLogin(page, email, testPassword);
  });

  async function createFriendAccount(
    page: any,
    name: string,
    balance: string,
    direction?: 'THEY_OWE_ME' | 'I_OWE_THEM',
  ) {
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', name);
    await page.selectOption('select[id="account-type"]', 'FRIEND_LENDING');

    if (direction) {
      await page.selectOption('select[id="lending-direction"]', direction);
    }

    await page.fill('input[id="initial-balance"]', balance);
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
  }

  test('"They owe me" creates positive balance and shows correct label', async ({ page }) => {
    await createFriendAccount(page, 'Bob', '500', 'THEY_OWE_ME');

    const card = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Bob' }) });
    await expect(card).toBeVisible();
    await expect(card.getByText('₹500.00')).toBeVisible();
    await expect(card.getByText('They owe you')).toBeVisible();
  });

  test('"I owe them" creates negative balance and shows correct label', async ({ page }) => {
    await createFriendAccount(page, 'Alice', '300', 'I_OWE_THEM');

    const card = page.locator('div.p-4', { has: page.getByRole('heading', { name: 'Alice' }) });
    await expect(card).toBeVisible();
    await expect(card.getByText('-₹300.00')).toBeVisible();
    await expect(card.getByText('You owe them')).toBeVisible();
  });

  test('net worth reflects combined friend/lending balances', async ({ page }) => {
    // Bob owes me 500 → +500 to net worth
    await createFriendAccount(page, 'Bob', '500', 'THEY_OWE_ME');

    // I owe Alice 300 → -300 to net worth
    await createFriendAccount(page, 'Alice', '300', 'I_OWE_THEM');

    // Net worth = 500 - 300 = 200
    const netWorthSection = page.getByText('Net Worth:', { exact: true }).first();
    await expect(netWorthSection).toBeVisible();

    // The net worth value is in the same container — grab it from the line next to the label
    const netWorthLine = netWorthSection.locator('..');
    await expect(netWorthLine.getByText('₹200.00')).toBeVisible();
  });

  test('net worth is negative when liabilities exceed assets', async ({ page }) => {
    // Bob owes me 100 → +100
    await createFriendAccount(page, 'Bob', '100', 'THEY_OWE_ME');

    // I owe Alice 400 → -400
    await createFriendAccount(page, 'Alice', '400', 'I_OWE_THEM');

    // Net worth = 100 - 400 = -300
    const netWorthLine = page.getByText('Net Worth:', { exact: true }).first().locator('..');
    await expect(netWorthLine.getByText('-₹300.00')).toBeVisible();
  });
});
