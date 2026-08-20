import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import apiClient from './client';

describe('apiClient 401 auto-logout', () => {
  const originalLocation = window.location;

  beforeEach(() => {
    vi.stubGlobal('location', {
      ...originalLocation,
      href: 'http://localhost/dashboard',
      pathname: '/dashboard',
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('clears the session, flags it as expired, and redirects to /login when a protected endpoint returns 401', async () => {
    localStorage.setItem('token', 'stale-token');
    localStorage.setItem('user', JSON.stringify({ id: '1', email: 'test@example.com' }));

    server.use(
      http.get('/api/v1/accounts', () => new HttpResponse(null, { status: 401 }))
    );

    await expect(apiClient.get('/accounts')).rejects.toThrow();

    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
    expect(sessionStorage.getItem('sessionExpired')).toBe('true');
    expect(window.location.href).toBe('/login');
  });

  it('does not redirect or flag the session when the 401 comes from the login request itself', async () => {
    await expect(
      apiClient.post('/auth/login', { email: 'error@example.com', password: 'wrong' })
    ).rejects.toThrow();

    expect(sessionStorage.getItem('sessionExpired')).toBeNull();
    expect(window.location.href).not.toBe('/login');
  });
});
