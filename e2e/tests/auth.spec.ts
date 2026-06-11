/**
 * Run these tests with:
 * make test-e2e
 */
import { test, expect } from '@playwright/test';
import { registerAndLogin, testPassword, uniqueEmail } from './helpers';

test.describe('Authentication Flow', () => {
  test('should allow a user to register and then login', async ({ page }) => {
    const email = uniqueEmail('auth-register');

    // ── 1. Register page renders correctly ──────────────────────────────────
    await page.goto('/register');
    await expect(page.locator('h1')).toHaveText('Create Account');

    // ── 2. Fill both password fields and submit ──────────────────────────────
    await page.fill('input[placeholder="Email"]', email);
    await page.fill('input[placeholder="Password"]', testPassword);
    await page.fill('input[placeholder="Confirm Password"]', testPassword);
    await page.click('button:has-text("Sign Up")');

    // ── 3. Redirected to Login ───────────────────────────────────────────────
    await expect(page).toHaveURL(/.*login/);
    await expect(page.locator('h1')).toHaveText('Login');

    // ── 4. Login with the same credentials ──────────────────────────────────
    await page.fill('input[placeholder="Email"]', email);
    await page.fill('input[placeholder="Password"]', testPassword);
    await page.click('button:has-text("Sign In")');

    // ── 5. Lands on Dashboard ────────────────────────────────────────────────
    await expect(page).toHaveURL(/.*dashboard/);
    await expect(page.getByRole('heading', { name: 'Accounts', exact: true })).toBeVisible();
  });

  test('should keep Sign Up button disabled while passwords do not match', async ({ page }) => {
    await page.goto('/register');

    await page.fill('input[placeholder="Email"]', uniqueEmail('auth-mismatch'));
    await page.fill('input[placeholder="Password"]', 'password123');
    await page.fill('input[placeholder="Confirm Password"]', 'different456');

    // Button must be disabled
    await expect(page.locator('button:has-text("Sign Up")')).toBeDisabled();

    // Inline mismatch error visible
    await expect(page.getByText('Passwords do not match.')).toBeVisible();
  });

  test('should enable Sign Up button once passwords match after a mismatch', async ({ page }) => {
    await page.goto('/register');

    await page.fill('input[placeholder="Email"]', uniqueEmail('auth-fix'));
    await page.fill('input[placeholder="Password"]', 'password123');
    await page.fill('input[placeholder="Confirm Password"]', 'wrong');

    // Still disabled with mismatch
    await expect(page.locator('button:has-text("Sign Up")')).toBeDisabled();

    // Correct the confirm field
    await page.fill('input[placeholder="Confirm Password"]', 'password123');

    // Now enabled
    await expect(page.locator('button:has-text("Sign Up")')).toBeEnabled();
    await expect(page.getByText('Passwords do not match.')).not.toBeVisible();
  });

  test('should not submit if Confirm Password is left empty', async ({ page }) => {
    await page.goto('/register');

    await page.fill('input[placeholder="Email"]', uniqueEmail('auth-empty-confirm'));
    await page.fill('input[placeholder="Password"]', 'password123');
    // intentionally leave Confirm Password blank

    // Button disabled because confirmPassword is empty and passwords differ ('' !== 'password123')
    await expect(page.locator('button:has-text("Sign Up")')).toBeDisabled();
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

  test('should show a link to Sign In from the Register page', async ({ page }) => {
    await page.goto('/register');
    await page.click('button:has-text("Sign In")');
    await expect(page).toHaveURL(/.*login/);
  });

  test('should show a link to Sign Up from the Login page', async ({ page }) => {
    await page.goto('/login');
    await page.click('button:has-text("Sign Up")');
    await expect(page).toHaveURL(/.*register/);
  });

  test('register page inputs should be readable in dark theme', async ({ page }) => {
    await page.goto('/register');

    // Switch to dark theme via the ThemeToggle on the register page
    const themeSelect = page.getByLabel('Select theme');
    await themeSelect.selectOption('dark');

    // Inputs must NOT have a forced black text colour — their color should come
    // from the CSS variable (foreground), not from a hardcoded value.
    const emailInput = page.locator('input[placeholder="Email"]');
    const color = await emailInput.evaluate((el) => getComputedStyle(el).color);
    // In dark theme the foreground is white/light — must not be 'rgb(0, 0, 0)'
    expect(color).not.toBe('rgb(0, 0, 0)');
  });
});
