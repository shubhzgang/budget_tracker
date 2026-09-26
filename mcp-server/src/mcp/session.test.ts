import { describe, it, expect, beforeEach } from 'vitest';
import request from 'supertest';
import { createApp } from '../index.js';
import { clearStores, storeToken } from '../oauth/store.js';
import { clearSessions } from './handler.js';

const app = createApp();

function fakeJwt(expSecondsFromNow: number, sub?: string): string {
  const header = Buffer.from(JSON.stringify({ alg: 'none' })).toString('base64url');
  const payload = Buffer.from(
    JSON.stringify({ sub, exp: Math.floor(Date.now() / 1000) + expSecondsFromNow }),
  ).toString('base64url');
  return `${header}.${payload}.sig`;
}

async function initializeSession(app: ReturnType<typeof createApp>, token: string): Promise<string> {
  const init = await request(app)
    .post('/mcp')
    .set({ Authorization: `Bearer ${token}`, Accept: 'application/json, text/event-stream' })
    .send({
      jsonrpc: '2.0',
      id: 1,
      method: 'initialize',
      params: { protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 'test', version: '0.0.0' } },
    });
  expect(init.status).toBe(200);
  return init.headers['mcp-session-id'] as string;
}

beforeEach(() => {
  clearStores();
  clearSessions();
});

describe('MCP session lifecycle', () => {
  it('initialize issues a session id and tools/list returns 19 tools', async () => {
    const token = storeToken(fakeJwt(3600));
    const auth = { Authorization: `Bearer ${token}` };

    const init = await request(app)
      .post('/mcp')
      .set({ ...auth, Accept: 'application/json, text/event-stream' })
      .send({
        jsonrpc: '2.0',
        id: 1,
        method: 'initialize',
        params: {
          protocolVersion: '2025-03-26',
          capabilities: {},
          clientInfo: { name: 'test', version: '0.0.0' },
        },
      });
    expect(init.status).toBe(200);
    const sessionId = init.headers['mcp-session-id'] as string | undefined;
    expect(sessionId).toBeTruthy();

    const tools = await request(app)
      .post('/mcp')
      .set({ ...auth, Accept: 'application/json, text/event-stream', 'mcp-session-id': sessionId! })
      .send({ jsonrpc: '2.0', id: 2, method: 'notifications/initialized' })
      .then(() =>
        request(app)
          .post('/mcp')
          .set({ ...auth, Accept: 'application/json, text/event-stream', 'mcp-session-id': sessionId! })
          .send({ jsonrpc: '2.0', id: 3, method: 'tools/list', params: {} }),
      );
    expect(tools.status).toBe(200);
    const bodyText = `${tools.text ?? ''} ${JSON.stringify(tools.body ?? {})}`;
    for (const name of [
      'list_accounts',
      'get_account',
      'list_transactions',
      'get_transaction',
      'create_transaction',
      'update_transaction',
      'delete_transaction',
      'get_expenditure_summary',
      'list_transfers',
      'get_transfer',
      'create_transfer',
      'update_transfer',
      'delete_transfer',
      'get_activity_feed',
      'list_categories',
      'create_category',
      'update_category',
      'delete_category',
      'list_labels',
    ]) {
      expect(bodyText).toContain(name);
    }
    // Read-only contract: the 6 account/label write tools must never surface at the HTTP layer.
    for (const gone of [
      'create_account',
      'update_account',
      'delete_account',
      'create_label',
      'update_label',
      'delete_label',
    ]) {
      expect(bodyText).not.toContain(gone);
    }
  });

  it('non-initialize request without session returns 400', async () => {
    const token = storeToken(fakeJwt(3600));
    const res = await request(app)
      .post('/mcp')
      .set({ Authorization: `Bearer ${token}`, Accept: 'application/json, text/event-stream' })
      .send({ jsonrpc: '2.0', id: 1, method: 'tools/list', params: {} });
    expect(res.status).toBe(400);
  });
});

describe('MCP session principal binding', () => {
  it('POST with another principal token on an existing session returns 401', async () => {
    const alice = storeToken(fakeJwt(3600, 'alice'));
    const bob = storeToken(fakeJwt(3600, 'bob'));
    const sessionId = await initializeSession(app, alice);

    const res = await request(app)
      .post('/mcp')
      .set({ Authorization: `Bearer ${bob}`, Accept: 'application/json, text/event-stream', 'mcp-session-id': sessionId })
      .send({ jsonrpc: '2.0', id: 2, method: 'tools/list', params: {} });
    expect(res.status).toBe(401);
    expect(res.headers['www-authenticate']).toContain('Bearer');
  });

  it('GET with another principal token on an existing session returns 401', async () => {
    const alice = storeToken(fakeJwt(3600, 'alice'));
    const bob = storeToken(fakeJwt(3600, 'bob'));
    const sessionId = await initializeSession(app, alice);

    const res = await request(app)
      .get('/mcp')
      .set({ Authorization: `Bearer ${bob}`, Accept: 'text/event-stream', 'mcp-session-id': sessionId });
    expect(res.status).toBe(401);
  });

  it('DELETE with another principal token on an existing session returns 401', async () => {
    const alice = storeToken(fakeJwt(3600, 'alice'));
    const bob = storeToken(fakeJwt(3600, 'bob'));
    const sessionId = await initializeSession(app, alice);

    const res = await request(app)
      .delete('/mcp')
      .set({ Authorization: `Bearer ${bob}`, 'mcp-session-id': sessionId });
    expect(res.status).toBe(401);
  });

  it('owner principal keeps full access to its session', async () => {
    const alice = storeToken(fakeJwt(3600, 'alice'));
    const sessionId = await initializeSession(app, alice);

    await request(app)
      .post('/mcp')
      .set({ Authorization: `Bearer ${alice}`, Accept: 'application/json, text/event-stream', 'mcp-session-id': sessionId })
      .send({ jsonrpc: '2.0', id: 2, method: 'notifications/initialized' });
    const tools = await request(app)
      .post('/mcp')
      .set({ Authorization: `Bearer ${alice}`, Accept: 'application/json, text/event-stream', 'mcp-session-id': sessionId })
      .send({ jsonrpc: '2.0', id: 3, method: 'tools/list', params: {} });
    expect(tools.status).toBe(200);
    expect(`${tools.text ?? ''} ${JSON.stringify(tools.body ?? {})}`).toContain('list_accounts');
  });

  it('second token for the same principal can use the session', async () => {
    const alice1 = storeToken(fakeJwt(3600, 'alice'));
    const alice2 = storeToken(fakeJwt(3600, 'alice'));
    const sessionId = await initializeSession(app, alice1);

    await request(app)
      .post('/mcp')
      .set({ Authorization: `Bearer ${alice2}`, Accept: 'application/json, text/event-stream', 'mcp-session-id': sessionId })
      .send({ jsonrpc: '2.0', id: 2, method: 'notifications/initialized' });
    const tools = await request(app)
      .post('/mcp')
      .set({ Authorization: `Bearer ${alice2}`, Accept: 'application/json, text/event-stream', 'mcp-session-id': sessionId })
      .send({ jsonrpc: '2.0', id: 3, method: 'tools/list', params: {} });
    expect(tools.status).toBe(200);
  });
});
