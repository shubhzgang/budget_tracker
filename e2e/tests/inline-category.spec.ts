/**
 * E2E tests for inline category creation in the transaction form.
 *
 * Run with: make test-e2e
 */
import { test, expect } from '@playwright/test';
import { registerAndLogin, testPassword, uniqueEmail } from './helpers';

test.describe('Inline Category Creation', () => {
  test.beforeEach(async ({ page }) => {
    await registerAndLogin(page, uniqueEmail('inlinecat'), testPassword);
    // Create an account so a transaction can be added
    await page.click('button:has-text("Add Account")');
    await page.fill('input[id="account-name"]', 'Main Bank');
    await page.fill('input[id="initial-balance"]', '1000');
    await page.click('button[type="submit"]:has-text("Create Account")');
    await expect(page.getByRole('heading', { name: 'Create New Account' })).toBeHidden();
  });

  test('should offer a "New category" option in the transaction category select', async ({ page }) => {
    await page.click('button[aria-label="Add Transaction"]');
    // Options of a closed <select> are not "visible", so assert attachment instead
    const newCategoryOption = page.locator('select[id="trans-category"] option[value="__new__"]');
    await expect(newCategoryOption).toBeAttached();
    await expect(newCategoryOption).toHaveText('+ New category…');
  });

  test('should create a category inline and select it for the new transaction', async ({ page }) => {
    await page.click('button[aria-label="Add Transaction"]');
    await page.fill('input[id="trans-amount"]', '120');
    await page.fill('input[id="trans-desc"]', 'Book purchase');

    await page.selectOption('select[id="trans-category"]', { label: '+ New category…' });

    // Quick-add row appears
    const nameInput = page.getByLabel('New category name');
    await expect(nameInput).toBeVisible();

    // Paste an emoji that is not part of the picker preset dataset
    await page.locator('input[aria-label="Emoji"]').fill('📚');

    await nameInput.fill('Books');
    await page.getByRole('button', { name: 'Add', exact: true }).click();

    // The newly created category is selected in the dropdown
    const checkedOption = page.locator('select[id="trans-category"] option:checked');
    await expect(checkedOption).toHaveText(/📚 Books/);

    // Submit the transaction
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // The transaction shows up in the dashboard list
    await expect(page.getByText('Book purchase')).toBeVisible();
  });

  test('should select an emoji from the picker grid inside the quick-add row', async ({ page }) => {
    await page.click('button[aria-label="Add Transaction"]');
    await page.fill('input[id="trans-amount"]', '40');

    await page.selectOption('select[id="trans-category"]', { label: '+ New category…' });

    // Open the picker from the quick-add row
    await page.getByRole('button', { name: 'Pick emoji' }).click();
    await expect(page.locator('input[placeholder="Search emoji…"]')).toBeVisible();

    // Search by keyword and select from the grid
    await page.fill('input[placeholder="Search emoji…"]', 'pizza');
    await page.click('button[title="🍕"]');
    await expect(page.locator('input[placeholder="Search emoji…"]')).not.toBeVisible();

    // The quick-add emoji input now shows the selected emoji
    await expect(page.locator('input[aria-label="Emoji"]')).toHaveValue('🍕');

    await page.getByLabel('New category name').fill('Dining');
    await page.getByRole('button', { name: 'Add', exact: true }).click();

    const checkedOption = page.locator('select[id="trans-category"] option:checked');
    await expect(checkedOption).toHaveText(/🍕 Dining/);
  });

  test('should keep the inline-created category available when the modal is reopened', async ({ page }) => {
    // First open: create the category inline and submit the transaction
    await page.click('button[aria-label="Add Transaction"]');
    await page.fill('input[id="trans-amount"]', '10');
    await page.selectOption('select[id="trans-category"]', { label: '+ New category…' });
    await page.getByLabel('New category name').fill('Stationery');
    await page.getByRole('button', { name: 'Add', exact: true }).click();
    await page.click('button[type="submit"]:has-text("Add Transaction")');
    await expect(page.getByRole('heading', { name: 'Add Transaction' })).toBeHidden();

    // Reopen the modal: Layout has refetched, so the new category is in the dropdown
    await page.click('button[aria-label="Add Transaction"]');
    const option = page
      .locator('select[id="trans-category"] option')
      .filter({ hasText: 'Stationery' });
    await expect(option).toBeAttached();
  });

  test('should reject a duplicate name in the quick-add row and keep it open', async ({ page }) => {
    await page.click('button[aria-label="Add Transaction"]');
    await page.fill('input[id="trans-amount"]', '5');

    await page.selectOption('select[id="trans-category"]', { label: '+ New category…' });
    await page.getByLabel('New category name').fill('Food');

    const dialogPromise = page.waitForEvent('dialog');

    await page.getByRole('button', { name: 'Add', exact: true }).click();

    // The backend duplicate error is shown and the row stays open for correction
    const dialog = await dialogPromise;
    expect(dialog.message()).toBe('A category named "Food" already exists');
    await dialog.accept();
    await expect(page.getByLabel('New category name')).toBeVisible();
    await expect(page.locator('select[id="trans-category"]')).toHaveValue('__new__');
  });

  test('should allow cancelling inline category creation', async ({ page }) => {
    await page.click('button[aria-label="Add Transaction"]');

    const categorySelect = page.locator('select[id="trans-category"]');
    await categorySelect.selectOption({ label: '+ New category…' });
    await page.getByLabel('New category name').fill('Never Saved');

    await page.getByRole('button', { name: 'Cancel new category' }).click();

    // Quick-add row is gone and the previous category selection is restored
    await expect(page.getByLabel('New category name')).not.toBeVisible();
    await expect(categorySelect).not.toHaveValue('__new__');

    // Close the modal and verify no category was created
    await page.click('button:has-text("Cancel")');
    await page.click('nav >> text=Settings');
    await page.click('button:has-text("Categories")');
    await expect(page.getByText('Never Saved')).not.toBeVisible();
  });
});
