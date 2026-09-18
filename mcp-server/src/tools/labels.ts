import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { BudgetTrackerClient } from '../api/client.js';

export function registerLabelTools(server: McpServer, jwt: string, baseUrl: string) {
  const client = new BudgetTrackerClient(baseUrl, jwt);
  server.registerTool(
    'list_labels',
    { description: 'List all labels (read-only)', inputSchema: {} },
    async () => ({
      content: [{ type: 'text' as const, text: JSON.stringify(await client.listLabels(), null, 2) }],
    }),
  );
}
