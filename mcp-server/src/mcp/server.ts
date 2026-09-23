import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { registerAccountTools } from '../tools/accounts.js';
import { registerTransactionTools } from '../tools/transactions.js';
import { registerTransferTools } from '../tools/transfers.js';
import { registerActivityTools } from '../tools/activity.js';
import { registerCategoryTools } from '../tools/categories.js';
import { registerLabelTools } from '../tools/labels.js';
import { type MakeClient, defaultMakeClient } from '../api/client.js';

export function createMcpServer(jwt: string, baseUrl: string, makeClient: MakeClient = defaultMakeClient): McpServer {
  const server = new McpServer({ name: 'budget-tracker', version: '1.0.0' });
  registerAccountTools(server, jwt, baseUrl, makeClient);
  registerTransactionTools(server, jwt, baseUrl, makeClient);
  registerTransferTools(server, jwt, baseUrl, makeClient);
  registerActivityTools(server, jwt, baseUrl, makeClient);
  registerCategoryTools(server, jwt, baseUrl, makeClient);
  registerLabelTools(server, jwt, baseUrl, makeClient);
  return server;
}
