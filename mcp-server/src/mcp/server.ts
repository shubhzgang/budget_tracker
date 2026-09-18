import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { registerAccountTools } from '../tools/accounts.js';
import { registerTransactionTools } from '../tools/transactions.js';
import { registerTransferTools } from '../tools/transfers.js';
import { registerActivityTools } from '../tools/activity.js';
import { registerCategoryTools } from '../tools/categories.js';
import { registerLabelTools } from '../tools/labels.js';

export function createMcpServer(jwt: string, baseUrl: string): McpServer {
  const server = new McpServer({ name: 'budget-tracker', version: '1.0.0' });
  registerAccountTools(server, jwt, baseUrl);
  registerTransactionTools(server, jwt, baseUrl);
  registerTransferTools(server, jwt, baseUrl);
  registerActivityTools(server, jwt, baseUrl);
  registerCategoryTools(server, jwt, baseUrl);
  registerLabelTools(server, jwt, baseUrl);
  return server;
}
