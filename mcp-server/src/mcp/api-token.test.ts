import { describe, it, expect, beforeEach, vi } from 'vitest';
import request from 'supertest';
import { createApp } from '../index.js';
import { type MakeClient } from '../api/client.js';
import { clearStores } from '../oauth/store.js';
import { clearSessions } from './handler.js';

const RW = `bt_rw_${'a'.repeat(64)}`;
const RO = `bt_ro_${'b'.repeat(64)}`;
const ACCEPT = 'application/json, text/event-stream';

function initialize(app: ReturnType<typeof createApp>, token: string) {
  return request(app)
    .post('/mcp')
    .set({ Authorization: `Bearer ${token}`, Accept: ACCEPT })
    .send({
      jsonrpc: '2.0',
      id: 1,
      method: 'initialize',
      params: { protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 'test', version: '0.0.0' } },
    });
}

function fakeClients(listLabels: () => Promise<unknown>, listAccounts = async () => [{ id: 'acc-1' }]) {
  return vi.fn<MakeClient>(() => ({ listLabels, listAccounts }) as never);
}

beforeEach(() => {
  clearStores();
  clearSessions();
});

describe('Budget Tracker API tokens', () => {
  it('passes a full-access token straight through to the backend client', async () => {
    const makeClient = fakeClients(async () => []);
    const app = createApp(makeClient);

    const init = await initialize(app, RW);
    expect(init.status).toBe(200);
    const sessionId = init.headers['mcp-session-id'] as string;

    const call = await request(app)
      .post('/mcp')
      .set({ Authorization: `Bearer ${RW}`, Accept: ACCEPT, 'mcp-session-id': sessionId })
      .send({ jsonrpc: '2.0', id: 2, method: 'tools/call', params: { name: 'list_accounts', arguments: {} } });
    expect(call.status).toBe(200);
    expect(call.text).toContain('acc-1');
    expect(makeClient.mock.calls.every(([, token]) => token === RW)).toBe(true);
  });

  it('accepts read-only tokens too', async () => {
    const init = await initialize(createApp(fakeClients(async () => [])), RO);
    expect(init.status).toBe(200);
  });

  it('rejects a token the backend does not accept, without opening a session', async () => {
    const app = createApp(fakeClients(async () => Promise.reject(new Error('API error 401: '))));
    const init = await initialize(app, RW);
    expect(init.status).toBe(401);
    expect(init.headers['mcp-session-id']).toBeUndefined();
  });

  it('still opens the session when the backend is unreachable', async () => {
    const app = createApp(fakeClients(async () => Promise.reject(new Error('fetch failed'))));
    expect((await initialize(app, RW)).status).toBe(200);
  });

  it('does not let another token use the session', async () => {
    const app = createApp(fakeClients(async () => []));
    const sessionId = (await initialize(app, RW)).headers['mcp-session-id'] as string;
    const other = await request(app)
      .post('/mcp')
      .set({ Authorization: `Bearer ${RO}`, Accept: ACCEPT, 'mcp-session-id': sessionId })
      .send({ jsonrpc: '2.0', id: 2, method: 'tools/list', params: {} });
    expect(other.status).toBe(401);
  });
});
