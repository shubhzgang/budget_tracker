/**
 * Shared helpers for E2E tests.
 *
 * registerAndLogin() performs a full register + login flow for a fresh user
 * and lands on the dashboard. It correctly fills both the Password and
 * Confirm Password fields introduced in the register form redesign.
 */
import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';

export async function registerAndLogin(
  page: Page,
  email: string,
  password: string,
): Promise<void> {
  // ── Register ────────────────────────────────────────────────────────────────
  await page.goto('/register');
  await page.fill('input[placeholder="Email"]', email);
  await page.fill('input[placeholder="Password"]', password);
  await page.fill('input[placeholder="Confirm Password"]', password);
  await page.click('button:has-text("Sign Up")');

  // ── Login ───────────────────────────────────────────────────────────────────
  await expect(page).toHaveURL(/.*login/);
  await page.fill('input[placeholder="Email"]', email);
  await page.fill('input[placeholder="Password"]', password);
  await page.click('button:has-text("Sign In")');
  await expect(page).toHaveURL(/.*dashboard/);
}

export const testPassword = 'password123';

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 10000)}@example.com`;
}
