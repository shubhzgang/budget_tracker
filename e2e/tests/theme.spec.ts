import { test, expect } from '@playwright/test';

test.describe('Theme Selection', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should switch to dark theme and change background color', async ({ page }) => {
    const select = page.getByLabel('Select theme');
    await select.selectOption('dark');
    await expect(page.locator('html')).toHaveClass(/dark/);
    const body = page.locator('body');
    await expect(body).toHaveCSS('background-color', 'rgb(2, 6, 23)');
  });

  test('should switch to oled theme and change background color', async ({ page }) => {
    const select = page.getByLabel('Select theme');
    await select.selectOption('oled');
    await expect(page.locator('html')).toHaveClass(/oled/);
    const body = page.locator('body');
    await expect(body).toHaveCSS('background-color', 'rgb(0, 0, 0)');
  });

  test('should switch to light theme', async ({ page }) => {
    const select = page.getByLabel('Select theme');
    await select.selectOption('light');
    await expect(page.locator('html')).toHaveClass(/light/);
    const body = page.locator('body');
    await expect(body).toHaveCSS('background-color', 'rgb(255, 255, 255)');
  });

  test('should persist theme after reload', async ({ page }) => {
    const select = page.getByLabel('Select theme');
    await select.selectOption('dark');
    await expect(page.locator('html')).toHaveClass(/dark/);
    const body = page.locator('body');
    await expect(body).toHaveCSS('background-color', 'rgb(2, 6, 23)');

    await page.reload();
    await expect(page.locator('html')).toHaveClass(/dark/);
    await expect(body).toHaveCSS('background-color', 'rgb(2, 6, 23)');
  });
});
