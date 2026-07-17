/**
 * E2E tests for emoji keyword search in the category emoji picker.
 * Verifies that typing words (e.g., "pizza", "food", "cat") finds emoji,
 * not just pasting the actual emoji character.
 *
 * Run with: make test-e2e
 */
import { test, expect } from '@playwright/test';
import { registerAndLogin, testPassword, uniqueEmail } from './helpers';

test.describe('Emoji Keyword Search', () => {
  test.beforeEach(async ({ page }) => {
    await registerAndLogin(page, uniqueEmail('emoji'), testPassword);
    await page.click('nav >> text=Settings');
    await page.click('button:has-text("Categories")');
  });

  test('should find emoji by keyword search (pizza)', async ({ page }) => {
    await page.click('button:has-text("Pick emoji")');
    await page.fill('input[placeholder="Search emoji…"]', 'pizza');

    await expect(page.getByText('Search Results')).toBeVisible();
    // Pizza emoji should appear
    await expect(page.locator('button[title="🍕"]')).toBeVisible();
  });

  test('should find multiple emoji when searching by section keyword (food)', async ({ page }) => {
    await page.click('button:has-text("Pick emoji")');
    await page.fill('input[placeholder="Search emoji…"]', 'food');

    await expect(page.getByText('Search Results')).toBeVisible();

    // Multiple food-related emoji should be visible
    const emojiButtons = page.locator('button[title="🍕"], button[title="🍔"], button[title="🍎"]');
    await expect(emojiButtons.first()).toBeVisible();
  });

  test('should find emoji by animal keyword (cat)', async ({ page }) => {
    await page.click('button:has-text("Pick emoji")');
    await page.fill('input[placeholder="Search emoji…"]', 'cat');

    await expect(page.getByText('Search Results')).toBeVisible();
    // Cat emoji should appear
    await expect(page.locator('button[title="🐱"]')).toBeVisible();
  });

  test('should find emoji by section label (animals)', async ({ page }) => {
    await page.click('button:has-text("Pick emoji")');
    await page.fill('input[placeholder="Search emoji…"]', 'animals');

    await expect(page.getByText('Search Results')).toBeVisible();
    // Animal emojis from the "Animals & Nature" section should appear
    await expect(page.locator('button[title="🐶"]')).toBeVisible();
  });

  test('should be case-insensitive', async ({ page }) => {
    await page.click('button:has-text("Pick emoji")');
    await page.fill('input[placeholder="Search emoji…"]', 'PIZZA');

    await expect(page.getByText('Search Results')).toBeVisible();
    await expect(page.locator('button[title="🍕"]')).toBeVisible();
  });

  test('should create category using keyword search for emoji', async ({ page }) => {
    // Type a category name
    await page.fill('input[placeholder="e.g. Groceries"]', 'Dining Out');

    // Open picker and search by keyword
    await page.click('button:has-text("Pick emoji")');
    await page.fill('input[placeholder="Search emoji…"]', 'pizza');
    await page.click('button[title="🍕"]');

    // Picker closes, trigger shows selected emoji
    const trigger = page.locator('button:has-text("Pick emoji")');
    await expect(trigger).toContainText('🍕');

    // Submit
    await page.click('button:has-text("Add")');

    // Category appears with emoji
    const card = page.locator('.group', { hasText: 'Dining Out' });
    await expect(card).toBeVisible();
    await expect(card.getByText('🍕')).toBeVisible();
  });
});
