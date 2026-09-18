import { describe, it, expect, beforeEach } from 'vitest';
import request from 'supertest';
import { createApp } from '../index.js';
import { clearStores, storeToken } from '../oauth/store.js';
import { clearSessions } from './handler.js';

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
