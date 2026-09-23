import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { type MakeClient, defaultMakeClient } from '../api/client.js';

function textResult(data: unknown) {
  return { content: [{ type: 'text' as const, text: JSON.stringify(data, null, 2) }] };
}

const transferInput = {
  fromAccountId: z.string().uuid(),
  toAccountId: z.string().uuid(),
  fromAmount: z.number().optional(),
  toAmount: z.number().optional(),
  adjustment: z.number().optional(),
  transactionDate: z.string(),
  description: z.string(),
  categoryId: z.string().uuid().optional(),
  labelIds: z.array(z.string().uuid()).optional(),
};

const TRANSFER_HINT =
  'Provide exactly 2 of fromAmount, toAmount, adjustment. The 3rd is auto-computed (adjustment = toAmount - fromAmount).';

function paramsOf(args: Record<string, unknown>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(args)) {
    if (v !== undefined && v !== null) out[k] = String(v);
  }
  return out;
}

export function registerTransferTools(
  server: McpServer,
  jwt: string,
  baseUrl: string,
  makeClient: MakeClient = defaultMakeClient,
) {
  const client = makeClient(baseUrl, jwt);

  server.registerTool(
    'list_transfers',
    {
      description: 'List transfers with optional search, date range and pagination',
      inputSchema: {
        search: z.string().optional(),
        startDate: z.string().optional(),
        endDate: z.string().optional(),
        page: z.number().optional().default(0),
        size: z.number().optional().default(20),
      },
    },
    async (args) => textResult(await client.listTransfers(paramsOf(args as Record<string, unknown>))),
  );
  server.registerTool(
    'get_transfer',
    { description: 'Get a single transfer by ID', inputSchema: { id: z.string().uuid() } },
    async ({ id }) => textResult(await client.getTransfer(id)),
  );
  server.registerTool(
    'create_transfer',
    { description: `Create a transfer between two different accounts. ${TRANSFER_HINT}`, inputSchema: transferInput },
    async (args) => textResult(await client.createTransfer(args)),
  );
  server.registerTool(
    'update_transfer',
    {
      description: `Update a transfer by ID. ${TRANSFER_HINT}`,
      inputSchema: { id: z.string().uuid(), ...transferInput },
    },
    async ({ id, ...rest }) => textResult(await client.updateTransfer(id, rest)),
  );
  server.registerTool(
    'delete_transfer',
    { description: 'Delete a transfer by ID', inputSchema: { id: z.string().uuid() } },
    async ({ id }) => {
      await client.deleteTransfer(id);
      return textResult({ deleted: id });
    },
  );
}
