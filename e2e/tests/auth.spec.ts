import { test, expect } from '@playwright/test';

test.describe('Authentication Flow', () => {
  const testEmail = `test-${Date.now()}@example.com`;
  const testPassword = 'password123';

  test('should allow a user to register and then login', async ({ page }) => {
    // 1. Go to Register page
    await page.goto('/register');
    await expect(page.locator('h1')).toHaveText('Register');

    // 2. Register
    await page.fill('input[placeholder="Email"]', testEmail);
    await page.fill('input[placeholder="Password"]', testPassword);
    await page.click('button:has-text("Sign Up")');

    // 3. Should be redirected to Login
    await expect(page).toHaveURL(/.*login/);
    await expect(page.locator('h1')).toHaveText('Login');

    // 4. Login
    await page.fill('input[placeholder="Email"]', testEmail);
    await page.fill('input[placeholder="Password"]', testPassword);
    await page.click('button:has-text("Sign In")');

    // 5. Should be redirected to Dashboard
    await expect(page).toHaveURL(/.*dashboard/);
    await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible();
  });

  test('should show error message on invalid login', async ({ page }) => {
    await page.goto('/login');
    
    await page.fill('input[placeholder="Email"]', 'nonexistent@example.com');
    await page.fill('input[placeholder="Password"]', 'wrongpassword');
    await page.click('button:has-text("Sign In")');

    // Should stay on login page and show error
    await expect(page).toHaveURL(/.*login/);
    await expect(page.getByText('Bad credentials')).toBeVisible();
  });
});
