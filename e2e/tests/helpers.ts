/**
 * Shared helpers for E2E tests.
 *
 * registerAndLogin() creates a unique user via the backend API (not the UI)
 * and then logs in via the login page. This keeps tests isolated while
 * the public register UI is removed.
 */
import type { Page, APIRequestContext } from '@playwright/test';
import { expect } from '@playwright/test';

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8811';

export async function registerAndLogin(
  page: Page,
  email: string,
  password: string,
): Promise<void> {
  // ── Register via API (no UI) ─────────────────────────────────────────────
  const context = page.context();
  await context.request.post(`${BACKEND_URL}/api/v1/auth/register`, {
    data: { email, password },
  });

  // ── Login via UI ─────────────────────────────────────────────────────────
  await page.goto('/login');
  await page.fill('input[placeholder="Email"]', email);
  await page.fill('input[placeholder="Password"]', password);
  await page.click('button:has-text("Sign In")');
  await expect(page).toHaveURL(/.*dashboard/);
}

export const testPassword = 'password123';

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 10000)}@example.com`;
}
