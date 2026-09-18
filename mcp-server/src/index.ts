import express from 'express';
import { metadataRouter } from './oauth/metadata.js';
import { registerRouter } from './oauth/register.js';
import { authorizeRouter } from './oauth/authorize.js';
import { tokenRouter } from './oauth/token.js';
import { mcpRouter } from './mcp/handler.js';

export function createApp() {
  const app = express();
  app.use(express.json());
  app.use(express.urlencoded({ extended: true }));

  const ALLOWED_ORIGIN = /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/;
  app.use((req, res, next) => {
    const origin = req.headers.origin;
    if (typeof origin === 'string' && origin && !ALLOWED_ORIGIN.test(origin)) {
      res.status(403).send('Forbidden: disallowed Origin');
      return;
    }
    next();
  });

  app.use(metadataRouter);
  app.use(registerRouter);
  app.use(authorizeRouter);
  app.use(tokenRouter);
  app.use(mcpRouter);

  app.get('/health', (_req, res) => res.json({ status: 'ok' }));

  return app;
}

const isMain = process.argv[1]?.endsWith('dist/index.js') || process.argv[1]?.endsWith('src/index.ts');

if (isMain) {
  const app = createApp();
  const port = parseInt(process.env.MCP_PORT || '3001', 10);
  const host = process.env.MCP_HOST || '127.0.0.1';
  app.listen(port, host, () => {
    console.log(`Budget Tracker MCP server running on http://localhost:${port}`);
    console.log(`MCP endpoint: http://localhost:${port}/mcp`);
    console.log(`Budget Tracker API: ${process.env.BUDGET_TRACKER_URL || 'http://localhost:3300'}`);
  });
}
