import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { type MakeClient, defaultMakeClient } from '../api/client.js';

function textResult(data: unknown) {
  return { content: [{ type: 'text' as const, text: JSON.stringify(data, null, 2) }] };
}

const transactionType = z.enum(['INCOME', 'EXPENSE', 'LEND', 'BORROW']);

const listSchema = {
  search: z.string().optional(),
  type: transactionType.optional(),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
  page: z.number().optional().default(0),
  size: z.number().optional().default(20),
};

const transactionInput = {
  accountId: z.string().uuid(),
  amount: z.number().positive(),
  type: transactionType,
  transactionDate: z.string(),
  description: z.string().optional(),
  categoryId: z.string().uuid().optional(),
  labelIds: z.array(z.string().uuid()).optional(),
};

function paramsOf(args: Record<string, unknown>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(args)) {
    if (v !== undefined && v !== null) out[k] = String(v);
  }
  return out;
}

export function registerTransactionTools(
  server: McpServer,
  jwt: string,
  baseUrl: string,
  makeClient: MakeClient = defaultMakeClient,
) {
  const client = makeClient(baseUrl, jwt);

  server.registerTool(
    'list_transactions',
    { description: 'List transactions with optional search, type, date range and pagination', inputSchema: listSchema },
    async (args) => textResult(await client.listTransactions(paramsOf(args as Record<string, unknown>))),
  );
  server.registerTool(
    'get_transaction',
    { description: 'Get a single transaction by ID', inputSchema: { id: z.string().uuid() } },
    async ({ id }) => textResult(await client.getTransaction(id)),
  );
  server.registerTool(
    'create_transaction',
    { description: 'Create an INCOME, EXPENSE, LEND or BORROW transaction', inputSchema: transactionInput },
    async (args) => textResult(await client.createTransaction(args)),
  );
  server.registerTool(
    'update_transaction',
    { description: 'Update a transaction by ID', inputSchema: { id: z.string().uuid(), ...transactionInput } },
    async ({ id, ...rest }) => textResult(await client.updateTransaction(id, rest)),
  );
  server.registerTool(
    'delete_transaction',
    { description: 'Delete a transaction by ID', inputSchema: { id: z.string().uuid() } },
    async ({ id }) => {
      await client.deleteTransaction(id);
      return textResult({ deleted: id });
    },
  );
  server.registerTool(
    'get_expenditure_summary',
    {
      description: 'Get expenditure totals for today, yesterday, this/last week and this/last month with per-label breakdowns',
      inputSchema: {},
    },
    async () => textResult(await client.getExpenditureSummary()),
  );
}
