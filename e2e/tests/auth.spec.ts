/**
 * Run these tests with:
 * make test-e2e
 */
import { test, expect } from '@playwright/test';
import { registerAndLogin, testPassword, uniqueEmail } from './helpers';

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

  test('should set the JWT cookie with HttpOnly, Secure, and Lax flags', async ({ page }) => {
    await registerAndLogin(page, uniqueEmail('cookie-flags'), testPassword);

    const jwt = (await page.context().cookies()).find((c) => c.name === 'jwt');
    expect(jwt).toBeDefined();
    expect(jwt?.httpOnly).toBe(true);
    expect(jwt?.secure).toBe(true);
    expect(jwt?.sameSite).toBe('Lax');
    expect(jwt?.path).toBe('/');
  });

  test('should show the register page when registration is enabled', async ({ page }) => {
    await page.goto('/register');
    await expect(page.locator('h1')).toHaveText('Register');
    await expect(page.getByTestId('register-email')).toBeVisible();
    await expect(page.getByTestId('register-password')).toBeVisible();
  });
});
