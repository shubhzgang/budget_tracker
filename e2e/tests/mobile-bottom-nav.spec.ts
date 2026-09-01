import { test, expect } from '@playwright/test';
import { registerAndLogin, testPassword, uniqueEmail } from './helpers';

test.describe('Mobile bottom navigation', () => {
  test.beforeEach(async ({ page }) => {
    await registerAndLogin(page, uniqueEmail('bottomnav'), testPassword);
  });

  test('bottom nav is visible on mobile viewport across pages', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });

    for (const path of ['/dashboard', '/transactions', '/settings']) {
      await page.goto(path);
      const bottomNav = page.getByTestId('nav-mobile-bottom');
      await expect(bottomNav).toBeVisible();
      await expect(bottomNav.getByTestId('bottom-nav-transactions')).toBeVisible();
      await expect(bottomNav.getByTestId('bottom-nav-dashboard')).toBeVisible();
      await expect(bottomNav.getByTestId('bottom-nav-settings')).toBeVisible();
    }
  });

  test('bottom nav links navigate on mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/dashboard');

    await page.getByTestId('bottom-nav-settings').click();
    await expect(page).toHaveURL(/.*settings/);

    await page.getByTestId('bottom-nav-transactions').click();
    await expect(page).toHaveURL(/.*transactions/);
  });

  test('active page is highlighted in bottom nav', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/settings');

    await expect(page.getByTestId('bottom-nav-settings')).toHaveClass(/active/);
    await expect(page.getByTestId('bottom-nav-dashboard')).not.toHaveClass(/active/);
  });

  test('bottom nav is hidden on desktop viewport', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/dashboard');

    await expect(page.getByTestId('nav-mobile-bottom')).toBeHidden();
    await expect(page.getByTestId('nav-desktop')).toBeVisible();
  });
});
