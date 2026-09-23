import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { type MakeClient, defaultMakeClient } from '../api/client.js';

function textResult(data: unknown) {
  return { content: [{ type: 'text' as const, text: JSON.stringify(data, null, 2) }] };
}

export function registerAccountTools(server: McpServer, jwt: string, baseUrl: string, makeClient: MakeClient = defaultMakeClient) {
  const client = makeClient(baseUrl, jwt);
  server.registerTool('list_accounts', { description: 'List all accounts with their balances', inputSchema: {} }, async () =>
    textResult(await client.listAccounts()),
  );
  server.registerTool(
    'get_account',
    { description: 'Get a single account by ID', inputSchema: { id: z.string().uuid() } },
    async ({ id }) => textResult(await client.getAccount(id)),
  );
}
