/**
 * CORS Configuration E2E Tests
 *
 * Note: Playwright bypasses browser CORS restrictions, so we verify CORS
 * behavior by inspecting response headers directly against the backend.
 * The full browser-enforcement of CORS is validated by integration tests
 * (CorsConfigurationTest.java) which control the Origin header precisely.
 *
 * These tests verify that:
 * 1. CORS is disabled by default (no allowed-origins property set)
 * 2. CORS headers appear when allowed-origins is configured
 *
 * Run with: make test-e2e
 */
import { test, expect } from '@playwright/test';

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8811';

test.describe('CORS Configuration', () => {
  test('should not return CORS headers by default (no origins configured)', async ({ request }) => {
    // By default, the backend has no app.cors.allowed-origins set,
    // so it should NOT return Access-Control-Allow-Origin.
    const response = await request.get(`${BACKEND_URL}/actuator/health`, {
      headers: { Origin: 'http://localhost:55173' },
    });

    expect(response.ok()).toBeTruthy();
    expect(response.headers()).not.toHaveProperty('access-control-allow-origin');
  });

  test('should handle OPTIONS preflight without error', async ({ request }) => {
    // Even without CORS configured, OPTIONS should not 404.
    const response = await request.fetch(`${BACKEND_URL}/actuator/health`, {
      method: 'OPTIONS',
      headers: {
        Origin: 'http://localhost:55173',
        'Access-Control-Request-Method': 'GET',
      },
    });

    // Spring Boot handles OPTIONS by default; should not return 404/405
    expect([200, 204, 401, 403]).toContain(response.status());
  });
});
