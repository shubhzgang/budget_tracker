import { describe, it, expect, beforeEach, vi } from 'vitest';
import request from 'supertest';
import { createApp } from '../index.js';
import { createMcpServer } from './server.js';
import { type MakeClient } from '../api/client.js';
import { clearStores, storeToken } from '../oauth/store.js';
import { clearSessions } from './handler.js';

const ALL_19 = [
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
];

function fakeJwt(expSecondsFromNow: number, subject: string): string {
  const header = Buffer.from(JSON.stringify({ alg: 'none' })).toString('base64url');
  const payload = Buffer.from(
    JSON.stringify({ sub: subject, exp: Math.floor(Date.now() / 1000) + expSecondsFromNow }),
  ).toString('base64url');
  return `${header}.${payload}.sig`;
}

beforeEach(() => {
  clearStores();
  clearSessions();
});

describe('read-only tool contract (server level)', () => {
  function toolNamesOf(server: ReturnType<typeof createMcpServer>): string[] {
    return Object.keys((server as unknown as { _registeredTools: Record<string, unknown> })._registeredTools);
  }

  it('registers exactly the 19 planned tools', () => {
    const server = createMcpServer(fakeJwt(3600), 'http://localhost:3300');
    const tools = toolNamesOf(server).sort();
    expect(tools).toEqual([...ALL_19].sort());
    expect(tools.length).toBe(19);
  });

  it('never registers any account/label write tool', () => {
    const server = createMcpServer(fakeJwt(3600), 'http://localhost:3300');
    const names = toolNamesOf(server);
    for (const gone of [
      'create_account',
      'update_account',
      'delete_account',
      'create_label',
      'update_label',
      'delete_label',
    ]) {
      expect(names).not.toContain(gone);
    }
  });
});

describe('per-session JWT isolation', () => {
  it('builds each session its own client bound to that bearer token\'s JWT (no module-level sharing)', async () => {
    const makeClient = vi.fn<MakeClient>(() => ({}) as never);
    const app = createApp(makeClient);

    const jwtA = fakeJwt(3600, 'user-a');
    const jwtB = fakeJwt(3600, 'user-b');
    const tokenA = storeToken(jwtA);
    const tokenB = storeToken(jwtB);

    for (const token of [tokenA, tokenB]) {
      const init = await request(app)
        .post('/mcp')
        .set({ Authorization: `Bearer ${token}`, Accept: 'application/json, text/event-stream' })
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
    }

    // Each session builds 6 clients (one per tool module). Every one of them must be
    // bound to that session's own JWT — no module-level sharing or cross-user bleed.
    expect(makeClient).toHaveBeenCalledTimes(12);
    const calledJwts = makeClient.mock.calls.map(([, jwt]) => jwt);
    expect(calledJwts.filter((j) => j === jwtA)).toHaveLength(6);
    expect(calledJwts.filter((j) => j === jwtB)).toHaveLength(6);
    expect(calledJwts).not.toContain(undefined);
  });
});
