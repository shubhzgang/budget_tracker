import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { BudgetTrackerClient } from '../api/client.js';

function textResult(data: unknown) {
  return { content: [{ type: 'text' as const, text: JSON.stringify(data, null, 2) }] };
}

export function registerCategoryTools(server: McpServer, jwt: string, baseUrl: string) {
  const client = new BudgetTrackerClient(baseUrl, jwt);
  server.registerTool('list_categories', { description: 'List all categories', inputSchema: {} }, async () =>
    textResult(await client.listCategories()),
  );
  server.registerTool(
    'create_category',
    { description: 'Create a category', inputSchema: { name: z.string(), icon: z.string().optional() } },
    async (args) => textResult(await client.createCategory(args)),
  );
  server.registerTool(
    'update_category',
    {
      description:
        'Update a category by ID. PUT replaces the whole object: always re-send the current icon (fetch via list_categories first) or the emoji will be erased.',
      inputSchema: { id: z.string().uuid(), name: z.string(), icon: z.string() },
    },
    async ({ id, ...rest }) => textResult(await client.updateCategory(id, rest)),
  );
  server.registerTool(
    'delete_category',
    {
      description:
        'Delete ANY category including defaults or in-use ones. Transactions using it are silently detached (category set to null). Confirm with the user first. Duplicate names are rejected (400, case-insensitive).',
      inputSchema: { id: z.string().uuid() },
    },
    async ({ id }) => {
      await client.deleteCategory(id);
      return textResult({ deleted: id });
    },
  );
}
