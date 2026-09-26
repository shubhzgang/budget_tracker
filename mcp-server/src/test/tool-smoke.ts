/**
 * Demo-data smoke: full OAuth flow + tool-call assertions against seeded demo data.
 * Exit 0 = pass. Run via `npm run smoke` (Makefile: `make test-mcp`).
 */
import { randomBytes, createHash } from 'node:crypto';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';

const MCP = process.env.MCP_URL || 'http://localhost:3001';
const EMAIL = process.env.TEST_EMAIL || 'test@example.com';
const PASSWORD = process.env.TEST_PASSWORD || 'password';
const CB = 'http://localhost:1/cb';

let failures = 0;
const check = (name: string, ok: boolean, detail = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `  (${detail})` : ''}`);
  if (!ok) failures++;
};
const num = (v: unknown) => Number(v ?? 0);
const nearly = (a: number, b: number, eps = 0.001) => Math.abs(a - b) < eps;
const text = (res: any) => JSON.parse(res.content[0].text);

async function login(): Promise<string> {
  const reg: any = await fetch(`${MCP}/register`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ client_name: 'smoke', redirect_uris: [CB] }),
  }).then(r => r.json());

  const verifier = randomBytes(32).toString('base64url');
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  const auth = await fetch(`${MCP}/authorize`, {
    method: 'POST', redirect: 'manual',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: reg.client_id, redirect_uri: CB, code_challenge: challenge,
      code_challenge_method: 'S256', state: 'smoke', email: EMAIL, password: PASSWORD,
    }),
  });
  const loc = auth.headers.get('location');
  if (!loc) throw new Error(`OAuth authorize failed (status ${auth.status}): no redirect`);
  const code = new URL(loc, CB).searchParams.get('code');

  const tok: any = await fetch(`${MCP}/token`, {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code', code: code!, redirect_uri: CB,
      client_id: reg.client_id, code_verifier: verifier,
    }),
  }).then(r => r.json());
  if (!tok.access_token) throw new Error(`OAuth failed: ${JSON.stringify(tok)}`);
  return tok.access_token;
}

async function main() {
  const client = new Client({ name: 'smoke', version: '1.0.0' });
  await client.connect(new StreamableHTTPClientTransport(new URL(`${MCP}/mcp`), {
    requestInit: { headers: { Authorization: `Bearer ${await login()}` } },
  }));
  const call = (name: string, args: any) => client.callTool({ name, arguments: args });

  // ── Read-only contract + seeded fixtures ────────────────────────────
  const tools = (await client.listTools()).tools.map(t => t.name);
  check('exactly 19 tools', tools.length === 19, String(tools.length));
  for (const gone of ['create_account', 'update_account', 'delete_account',
                      'create_label', 'update_label', 'delete_label'])
    check(`no ${gone} tool`, !tools.includes(gone));

  const accounts = text(await call('list_accounts', {}));
  check('demo accounts', accounts.map((a: any) => a.name).sort().join() ===
        ['Bob (Lend)', 'Cash', 'Main Bank', 'Visa Credit'].join());

  const labels = text(await call('list_labels', {}));
  check('demo labels', labels.map((l: any) => l.name).sort().join() ===
        'BILLS,FUN,NEEDS,SAVINGS,WANTS');

  const cats = text(await call('list_categories', {}));
  check('Food icon 🍔', cats.find((c: any) => c.name === 'Food')?.icon === '🍔');

  const summary = async () => text(await call('get_expenditure_summary', {}));
  const s0 = await summary();
  check('today ≥ seeded Lunch', num(s0.today) >= 25, `today=${s0.today}`);
  check('thisWeek ≥ today', num(s0.thisWeek) >= num(s0.today));
  check('thisMonth ≥ thisWeek', num(s0.thisMonth) >= num(s0.thisWeek));

  const txns = text(await call('list_transactions', { search: 'Salary credit', size: 50 }));
  check('salary credit ≥ 3', txns.totalElements >= 3, String(txns.totalElements));

  const feed = text(await call('get_activity_feed', { size: 10 }));
  check('Lunch in recent activity', feed.content.some((i: any) => i.description === 'Lunch'));

  const transfers = text(await call('list_transfers', { size: 50 }));
  const atm = transfers.content.find((t: any) => t.description === 'ATM Withdrawal');
  check('seeded transfer toAmount=55', !!atm && num(atm.fromAmount) === 50 && num(atm.toAmount) === 55);

  // ── Write path: summary must move by exactly +10 and come back ──────
  const mainBank = accounts.find((a: any) => a.name === 'Main Bank').id;
  const cash = accounts.find((a: any) => a.name === 'Cash').id;
  const base = num((await summary()).today);
  const created = text(await call('create_transaction', {
    accountId: mainBank, amount: 10, type: 'EXPENSE',
    transactionDate: new Date().toISOString(), description: 'MCP smoke tx',
  }));
  check('summary today +10', nearly(num((await summary()).today), base + 10), `base=${base}`);
  await call('delete_transaction', { id: created.id });
  check('summary back to baseline', nearly(num((await summary()).today), base));

  // ── Transfer auto-compute + error surface + cleanup ─────────────────
  const tr = text(await call('create_transfer', {
    fromAccountId: mainBank, toAccountId: cash, fromAmount: 40, adjustment: -2,
    transactionDate: new Date().toISOString(), description: 'MCP smoke transfer',
  }));
  check('toAmount auto-computed (40, −2) → 38', nearly(num(tr.toAmount), 38));
  await call('delete_transfer', { id: tr.id });

  const bad = await call('create_transfer', {
    fromAccountId: mainBank, toAccountId: mainBank, fromAmount: 5, toAmount: 5,
    transactionDate: new Date().toISOString(), description: 'should fail',
  });
  check('same-account transfer → isError', (bad as any).isError === true);

  const cat = text(await call('create_category', { name: 'ZZ MCP Smoke', icon: '🧪' }));
  const upd = text(await call('update_category', { id: cat.id, name: 'ZZ MCP Smoke', icon: '🧫' }));
  check('icon round-trips', upd.icon === '🧫');
  await call('delete_category', { id: cat.id });

  await client.close();
  console.log(failures ? `\n${failures} check(s) FAILED` : '\nAll checks passed');
  process.exit(failures ? 1 : 0);
}

main().catch(e => { console.error('Smoke run crashed:', e); process.exit(1); });
