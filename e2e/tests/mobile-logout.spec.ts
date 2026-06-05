import { test, expect } from '@playwright/test';

test.describe('Mobile Logout', () => {
  const testPassword = 'password123';

  test.beforeEach(async ({ page }) => {
    const testEmail = `test-${Date.now()}@example.com`;
    await page.goto('/register');
    await page.fill('input[placeholder="Email"]', testEmail);
    await page.fill('input[placeholder="Password"]', testPassword);
    await page.click('button:has-text("Sign Up")');
    await expect(page).toHaveURL(/.*login/);
    await page.fill('input[placeholder="Email"]', testEmail);
    await page.fill('input[placeholder="Password"]', testPassword);
    await page.click('button:has-text("Sign In")');
    await expect(page).toHaveURL(/.*dashboard/);
  });

  test('should show logout button on mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/dashboard');

    // Logout button should be visible in mobile header
    await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible();
  });

  test('logout button should redirect to login page', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/dashboard');

    await page.getByRole('button', { name: 'Logout' }).click();
    await expect(page).toHaveURL(/.*login/);
  });
});
