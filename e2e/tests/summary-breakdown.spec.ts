/**
 * Run these tests with:
 * make test-e2e
 */
import { test, expect } from '@playwright/test';
import { registerAndLogin, testPassword, uniqueEmail } from './helpers';

test.describe('Dashboard Expenditure Label Breakdown', () => {
  test.beforeEach(async ({ page }) => {
    const email = uniqueEmail('summarybreakdown');
    await registerAndLogin(page, email, testPassword);
  });

  async function createAccount(page) {
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Main');
    await page.fill('input[id="initial-balance"]', '1000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByText('Main')).toBeVisible();
  }

  /**
   * Adds an expense. When deselectNeeds is true, the default NEEDS label is
   * removed so the transaction is unlabelled.
   */
  async function addExpense(page, amount, desc, deselectNeeds) {
    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', amount);
    await page.fill('input[id="trans-desc"]', desc);
    if (deselectNeeds) {
      const labelButton = page.getByTestId('label-select-toggle');
      await labelButton.click();
      await page.waitForTimeout(500);
      const needsItem = page.getByTestId('label-dropdown').getByText('NEEDS');
      if (await needsItem.isVisible().catch(() => false)) {
        await needsItem.click();
      }
      await labelButton.click();
    }
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();
  }

  test('should show per-label breakdown with NEEDS and Unlabelled on Today card', async ({ page }) => {
    await createAccount(page);
    await page.click('nav >> text=Transactions');

    // Labelled expense (NEEDS is selected by default in the form)
    await addExpense(page, '50', 'Groceries', false);

    // Unlabelled expense (deselect NEEDS)
    await addExpense(page, '30', 'Snacks', true);

    // Go to the dashboard and inspect the Today period card breakdown
    await page.click('nav >> text=Dashboard');
    await expect(page).toHaveURL(/.*dashboard/);

    const todayCard = page.locator('.period-card').filter({ hasText: 'Today' });
    const breakdown = todayCard.locator('.period-breakdown');
    await expect(breakdown).toBeVisible();

    const breakdownText = await breakdown.textContent();
    expect(breakdownText).toContain('NEEDS');
    expect(breakdownText).toContain('Unlabelled');
  });
});
