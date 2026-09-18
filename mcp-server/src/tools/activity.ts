import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { BudgetTrackerClient } from '../api/client.js';

function textResult(data: unknown) {
  return { content: [{ type: 'text' as const, text: JSON.stringify(data, null, 2) }] };
}

export function registerActivityTools(server: McpServer, jwt: string, baseUrl: string) {
  const client = new BudgetTrackerClient(baseUrl, jwt);
  server.registerTool(
    'get_activity_feed',
    {
      description: 'Unified feed of transactions and transfers with search, type, account and date filters',
      inputSchema: {
        search: z.string().optional(),
        type: z.enum(['INCOME', 'EXPENSE', 'LEND', 'BORROW', 'TRANSFER']).optional(),
        accountId: z.string().uuid().optional(),
        startDate: z.string().optional(),
        endDate: z.string().optional(),
        page: z.number().optional().default(0),
        size: z.number().optional().default(20),
      },
    },
    async (args) => {
      const params: Record<string, string> = {};
      for (const [k, v] of Object.entries(args as Record<string, unknown>)) {
        if (v !== undefined && v !== null) params[k] = String(v);
      }
      return textResult(await client.getActivity(params));
    },
  );
}
