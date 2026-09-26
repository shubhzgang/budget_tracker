import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import request from 'supertest';
import { createApp } from '../index.js';
import { clearStores, storeToken } from '../oauth/store.js';
import { clearSessions } from '../mcp/handler.js';

const app = createApp();

function fakeJwt(expSecondsFromNow: number): string {
  const header = Buffer.from(JSON.stringify({ alg: 'none' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + expSecondsFromNow })).toString(
    'base64url',
  );
  return `${header}.${payload}.sig`;
}

beforeEach(() => {
  clearStores();
  clearSessions();
});

afterEach(() => vi.unstubAllGlobals());

describe('MCP transport auth + app guards', () => {
  it('POST /mcp without token returns 401 with WWW-Authenticate resource metadata pointer', async () => {
    const res = await request(app).post('/mcp').send({ jsonrpc: '2.0', id: 1, method: 'ping' });
    expect(res.status).toBe(401);
    expect(res.headers['www-authenticate']).toMatch(/resource_metadata=".*\/\.well-known\/oauth-protected-resource"/);
  });

  it('POST /mcp with expired budget JWT returns 401 with WWW-Authenticate', async () => {
    const token = storeToken(fakeJwt(-3600));
    const res = await request(app)
      .post('/mcp')
      .set('Authorization', `Bearer ${token}`)
      .send({ jsonrpc: '2.0', id: 1, method: 'initialize', params: {} });
    expect(res.status).toBe(401);
    expect(res.headers['www-authenticate']).toContain('Bearer');
  });

  it('rejects disallowed Origin header', async () => {
    const res = await request(app)
      .get('/.well-known/oauth-authorization-server')
      .set('Origin', 'http://evil.example');
    expect(res.status).toBe(403);
  });

  it('allows localhost Origin and no-Origin clients', async () => {
    const withOrigin = await request(app)
      .get('/.well-known/oauth-authorization-server')
      .set('Origin', 'http://localhost:5173');
    expect(withOrigin.status).toBe(200);
    const noOrigin = await request(app).get('/health');
    expect(noOrigin.status).toBe(200);
  });
});

describe('BudgetTrackerClient', () => {
  it('sends Bearer JWT and parses JSON', async () => {
    const { BudgetTrackerClient } = await import('../api/client.js');
    const fetchMock = vi.fn(async () => new Response(JSON.stringify([{ id: '1' }]), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const client = new BudgetTrackerClient('http://localhost:3300', 'jwt-123');
    const accounts = await client.listAccounts();
    expect(accounts).toEqual([{ id: '1' }]);
    expect(fetchMock).toHaveBeenCalledOnce();
    const [, options] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect((options.headers as Record<string, string>).Authorization).toBe('Bearer jwt-123');
  });

  it('throws with status body on API error and returns null on 204', async () => {
    const { BudgetTrackerClient } = await import('../api/client.js');
    vi.stubGlobal('fetch', vi.fn(async () => new Response('boom', { status: 500 })));
    await expect(new BudgetTrackerClient('http://x', 'j').listLabels()).rejects.toThrow('API error 500');

    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 204 })));
    await expect(new BudgetTrackerClient('http://x', 'j').listLabels()).resolves.toBeNull();
  });
});
