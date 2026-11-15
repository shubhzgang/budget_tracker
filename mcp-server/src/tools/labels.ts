import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { type MakeClient, defaultMakeClient } from '../api/client.js';
import { READ } from './annotations.js';

export function registerLabelTools(server: McpServer, jwt: string, baseUrl: string, makeClient: MakeClient = defaultMakeClient) {
  const client = makeClient(baseUrl, jwt);
  server.registerTool(
    'list_labels',
    { annotations: READ, description: 'List all labels (read-only)', inputSchema: {} },
    async () => ({
      content: [{ type: 'text' as const, text: JSON.stringify(await client.listLabels(), null, 2) }],
    }),
  );
}
