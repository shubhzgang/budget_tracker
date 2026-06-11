/**
 * E2E tests for Category management, including the full emoji picker.
 *
 * Run with: make test-e2e
 */
import { test, expect } from '@playwright/test';
import { registerAndLogin, testPassword, uniqueEmail } from './helpers';

test.describe('Category Management', () => {
  test.beforeEach(async ({ page }) => {
    await registerAndLogin(page, uniqueEmail('cat'), testPassword);
    // Navigate to Settings > Categories
    await page.click('nav >> text=Settings');
    await page.click('button:has-text("Categories")');
  });

  // ── Emoji picker interaction ─────────────────────────────────────────────────

  test('should open the emoji picker when the trigger is clicked', async ({ page }) => {
    await page.click('button:has-text("Pick emoji")');
    // Picker popover is visible
    await expect(page.locator('input[placeholder="Search emoji…"]')).toBeVisible();
  });

  test('should display all section tabs in the emoji picker', async ({ page }) => {
    await page.click('button:has-text("Pick emoji")');

    // Each section tab shows its representative emoji as a button title
    const expectedSections = [
      'Smileys & Emotion',
      'People & Body',
      'Animals & Nature',
      'Food & Drink',
      'Travel & Places',
      'Activities',
      'Objects',
      'Symbols',
      'Flags',
    ];
    for (const section of expectedSections) {
      await expect(page.locator(`button[title="${section}"]`)).toBeVisible();
    }
  });

  test('should filter emoji when searching', async ({ page }) => {
    await page.click('button:has-text("Pick emoji")');

    // Type a specific emoji character to search for it
    await page.fill('input[placeholder="Search emoji…"]', '🍕');

    // The search-results section label should appear
    await expect(page.getByText('Search Results')).toBeVisible();

    // The pizza emoji button should be visible in results
    await expect(page.locator('button[title="🍕"]')).toBeVisible();
  });

  test('should show no results message for an unmatched search', async ({ page }) => {
    await page.click('button:has-text("Pick emoji")');
    // Type something that won't match any emoji character
    await page.fill('input[placeholder="Search emoji…"]', 'xyzxyzxyz');
    await expect(page.getByText('No emoji found')).toBeVisible();
  });

  test('should update the trigger button when an emoji is selected', async ({ page }) => {
    await page.click('button:has-text("Pick emoji")');

    // Select the pizza emoji from the Food section
    await page.click('button[title="Food & Drink"]');
    await page.click('button[title="🍕"]');

    // Picker closes after selection
    await expect(page.locator('input[placeholder="Search emoji…"]')).not.toBeVisible();

    // The trigger button now shows the chosen emoji
    const trigger = page.locator('button:has-text("Pick emoji")');
    await expect(trigger).toContainText('🍕');
  });

  test('should close the picker on outside click', async ({ page }) => {
    await page.click('button:has-text("Pick emoji")');
    await expect(page.locator('input[placeholder="Search emoji…"]')).toBeVisible();

    // Click somewhere outside the popover
    await page.locator('h3:has-text("Add New Category")').click();
    await expect(page.locator('input[placeholder="Search emoji…"]')).not.toBeVisible();
  });

  // ── Category creation flow ───────────────────────────────────────────────────

  test('should create a new category with a custom emoji icon', async ({ page }) => {
    // Fill in the name
    await page.fill('input[placeholder="e.g. Groceries"]', 'Pizza Fund');

    // Open picker, go to Food section, pick pizza
    await page.click('button:has-text("Pick emoji")');
    await page.click('button[title="Food & Drink"]');
    await page.click('button[title="🍕"]');

    // Submit
    await page.click('button:has-text("Add")');

    // The new category should appear in the list with the correct emoji and name
    const categoryCard = page.locator('.group', { hasText: 'Pizza Fund' });
    await expect(categoryCard).toBeVisible();
    await expect(categoryCard.getByText('🍕')).toBeVisible();
  });

  test('should highlight the currently selected emoji in the picker', async ({ page }) => {
    await page.click('button:has-text("Pick emoji")');

    // Pick an emoji
    await page.click('button[title="Food & Drink"]');
    await page.click('button[title="🍕"]');

    // Re-open picker — the pizza button should have a highlighted ring class
    await page.click('button:has-text("Pick emoji")');
    await page.click('button[title="Food & Drink"]');
    const pizzaBtn = page.locator('button[title="🍕"]');
    await expect(pizzaBtn).toHaveClass(/ring-primary/);
  });

  test('should delete a non-default category', async ({ page }) => {
    // Create a category first
    await page.fill('input[placeholder="e.g. Groceries"]', 'Temporary Cat');
    await page.click('button:has-text("Add")');
    await expect(page.getByText('Temporary Cat')).toBeVisible();

    // Confirm the browser dialog
    page.on('dialog', (dialog) => dialog.accept());

    // Hover to reveal delete button and click it
    const card = page.locator('.group', { hasText: 'Temporary Cat' });
    await card.hover();
    await card.locator('button[title="Delete Category"]').click();

    // Category should be gone
    await expect(page.getByText('Temporary Cat')).not.toBeVisible();
  });

  test('should not show a delete button for default categories', async ({ page }) => {
    // Default categories come pre-seeded; find the "Default" badge
    const defaultCard = page.locator('.group', { hasText: 'Default' }).first();
    await defaultCard.hover();
    await expect(defaultCard.locator('button[title="Delete Category"]')).not.toBeVisible();
  });
});
