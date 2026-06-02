/**
 * Run these tests with:
 * make test-e2e
 */
import { test, expect } from '@playwright/test';

test.describe('Multi-Label Support', () => {
  const getTestEmail = () => `multilabel-${Date.now()}-${Math.floor(Math.random() * 1000)}@example.com`;
  const testPassword = 'password123';

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
    await expect(page).toHaveURL(/.*dashboard/);
  });

  test('should create a transaction with multiple labels', async ({ page }) => {
    // 1. Create an account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Checkings');
    await page.fill('input[id="initial-balance"]', '1000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByText('Checkings')).toBeVisible();

    // 2. Navigate to Settings > Labels to create a custom label
    await page.click('nav >> text=Settings');
    await page.click('button:has-text("Labels")');
    const nameInput = page.locator('form input[type="text"]');
    await nameInput.fill('Work');
    await page.click('button:has-text("Add")');
    await expect(page.getByText('Work')).toBeVisible();

    // 3. Add an expense with multiple labels
    await page.click('nav >> text=Transactions');
    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '50');
    await page.fill('input[id="trans-desc"]', 'Office supplies');

    // Open the label multi-select dropdown
    const labelButton = page.getByTestId('label-select-toggle');
    await labelButton.click();
    await page.waitForTimeout(500);

    // Click on the Work option in the dropdown
    await page.getByTestId('label-dropdown').getByText('Work').click();
    await labelButton.click(); // Close dropdown

    // Submit
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // 4. Verify transaction appears with label badges
    await expect(page.getByText('Office supplies')).toBeVisible();
    // Should show at least 2 label badges (NEEDS default + Work)
    const labelBadges = page.locator('span.bg-accent');
    const badgeCount = await labelBadges.count();
    expect(badgeCount).toBeGreaterThanOrEqual(2);
  });

  test('should reject label name with pipe character', async ({ page }) => {
    // Navigate to Settings > Labels
    await page.click('nav >> text=Settings');
    await page.click('button:has-text("Labels")');

    const nameInput = page.locator('form input[type="text"]');
    await nameInput.fill('Bad|Label');
    await page.click('button:has-text("Add")');

    // Should show error toast
    await expect(page.getByText(/cannot contain '\|'/)).toBeVisible({ timeout: 5000 });

    // The bad label should NOT appear in the list
    await expect(page.getByText('Bad|Label')).not.toBeVisible();
  });

  test('should search transactions by label', async ({ page }) => {
    // 1. Create an account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Main');
    await page.fill('input[id="initial-balance"]', '2000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByText('Main')).toBeVisible();

    // 2. Create a custom label
    await page.click('nav >> text=Settings');
    await page.click('button:has-text("Labels")');
    const nameInput = page.locator('form input[type="text"]');
    await nameInput.fill('Vacation');
    await page.click('button:has-text("Add")');
    await expect(page.getByText('Vacation')).toBeVisible();

    // 3. Add a transaction with Vacation label
    await page.click('nav >> text=Transactions');
    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '200');
    await page.fill('input[id="trans-desc"]', 'Flight ticket');

    // Select Vacation label
    const labelButton = page.getByTestId('label-select-toggle');
    await labelButton.click();
    await page.waitForTimeout(500);
    await page.getByTestId('label-dropdown').getByText('Vacation').click();
    await labelButton.click();

    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // 4. Add another transaction without Vacation label
    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '30');
    await page.fill('input[id="trans-desc"]', 'Lunch');
    // Deselect any pre-selected labels
    const lb2 = page.getByTestId('label-select-toggle');
    await lb2.click();
    await page.waitForTimeout(500);
    // Uncheck NEEDS if it's checked
    const needsItem = page.getByTestId('label-dropdown').getByText('NEEDS');
    if (await needsItem.isVisible().catch(() => false)) {
      await needsItem.click();
    }
    await lb2.click();

    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // 5. Search by "Vacation" label - should only show Flight ticket
    const searchInput = page.locator('input[placeholder*="Description"]');
    await searchInput.fill('Vacation');
    await expect(page.getByText('Flight ticket')).toBeVisible();
    await expect(page.getByText('Lunch')).not.toBeVisible();
  });

  test('should display multiple label badges on a transaction', async ({ page }) => {
    // 1. Create an account
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Savings');
    await page.fill('input[id="initial-balance"]', '500');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByText('Savings')).toBeVisible();

    // 2. Create a custom label
    await page.click('nav >> text=Settings');
    await page.click('button:has-text("Labels")');
    const nameInput = page.locator('form input[type="text"]');
    await nameInput.fill('Tax');
    await page.click('button:has-text("Add")');
    await expect(page.getByText('Tax')).toBeVisible();

    // 3. Add transaction with both NEEDS (default) and Tax
    await page.click('nav >> text=Transactions');
    await page.click('button:has-text("Add Transaction")');
    await page.fill('input[id="trans-amount"]', '75');
    await page.fill('input[id="trans-desc"]', 'Medical');

    const labelButton = page.getByTestId('label-select-toggle');
    await labelButton.click();
    await page.waitForTimeout(500);
   await page.getByTestId('label-dropdown').getByText('Tax').click();
    await labelButton.click();

    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // 4. Verify both label badges appear
    await expect(page.getByText('Medical')).toBeVisible();
    const labelBadges = page.locator('span.bg-accent');
    const badgeCount2 = await labelBadges.count();
    expect(badgeCount2).toBeGreaterThanOrEqual(2);
    // Both NEEDS and Tax should be visible in the badges
    await expect(page.getByText('NEEDS')).toBeVisible();
    await expect(page.getByText('Tax')).toBeVisible();
  });

  test('should create transfer with multiple labels', async ({ page }) => {
    // 1. Create two accounts
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Account A');
    await page.fill('input[id="initial-balance"]', '1000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByText('Account A')).toBeVisible();

    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Account B');
    await page.fill('input[id="initial-balance"]', '0');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByText('Account B')).toBeVisible();

    // 2. Create a custom label
    await page.click('nav >> text=Settings');
    await page.click('button:has-text("Labels")');
    const nameInput = page.locator('form input[type="text"]');
    await nameInput.fill('Monthly');
    await page.click('button:has-text("Add")');
    await expect(page.getByText('Monthly')).toBeVisible();

    // 3. Create a transfer with labels
    await page.click('nav >> text=Transactions');
    await page.click('button:has-text("Add Transaction")');
    await page.selectOption('select[id="trans-type"]', 'TRANSFER');
    await page.fill('input[id="trans-from-amount"]', '100');
    await page.fill('input[id="trans-adjustment"]', '0');
    await page.selectOption('select[id="trans-to-account"]', { label: 'Account B' });

    // Select Monthly label
    const labelButton = page.getByTestId('label-select-toggle');
    await labelButton.click();
    await page.waitForTimeout(500);
    await page.getByTestId('label-dropdown').getByText('Monthly').click();
    await labelButton.click();

    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // 4. Verify transfer appears with labels
    await expect(page.getByText('Account A → Account B')).toBeVisible();
    await expect(page.getByText('-₹100.00')).toBeVisible();
    // Should have at least 2 labels (NEEDS default + Monthly)
    const labelBadges3 = page.locator('span.bg-accent');
    const badgeCount3 = await labelBadges3.count();
    expect(badgeCount3).toBeGreaterThanOrEqual(2);
  });
});
