import { test, expect } from '@playwright/test';
import { registerAndLogin, testPassword, uniqueEmail } from './helpers';

test.describe('Mobile Logout', () => {
  const testPassword = 'password123';

  test.beforeEach(async ({ page }) => {
    await registerAndLogin(page, uniqueEmail('mobile'), testPassword);
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
