/**
 * Run these tests with:
 * make test-e2e
 */
import { test, expect } from '@playwright/test';
import { testPassword, uniqueEmail } from './helpers';

test.describe('Authentication Flow', () => {
  test('should show error message on invalid login', async ({ page }) => {
    await page.goto('/login');

    await page.fill('input[placeholder="Email"]', 'nonexistent@example.com');
    await page.fill('input[placeholder="Password"]', 'wrongpassword');
    await page.click('button:has-text("Sign In")');

    // Should stay on login page and show error
    await expect(page).toHaveURL(/.*login/);
    await expect(page.getByText('Bad credentials')).toBeVisible();
  });

  test('should reject navigation to /register', async ({ page }) => {
    await page.goto('/register');
    // The register route no longer exists in the frontend, so React Router
    // has no match for it — there should be no h1 on the page.
    await expect(page.locator('h1')).toHaveCount(0);
  });
});
