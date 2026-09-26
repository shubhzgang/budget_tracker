import { randomUUID } from 'node:crypto';
import { Router, type Request, type Response } from 'express';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { isInitializeRequest } from '@modelcontextprotocol/sdk/types.js';
import { createMcpServer } from './server.js';
import { type MakeClient, defaultMakeClient } from '../api/client.js';
import { getJwtForToken } from '../oauth/store.js';
import { send401 } from '../oauth/metadata.js';

export function isJwtExpired(token: string): boolean {
  try {
    const payloadBase64 = token.split('.')[1];
    const payload = JSON.parse(Buffer.from(payloadBase64, 'base64url').toString('utf8')) as { exp?: number };
    if (typeof payload.exp !== 'number') return true;
    return payload.exp * 1000 < Date.now();
  } catch {
    return true;
  }
}

function requireBearer(req: Request, res: Response): string | null {
  const authHeader = req.headers.authorization;
  if (!authHeader?.startsWith('Bearer ')) {
    send401(res, 'Missing Bearer token');
    return null;
  }
  const jwt = getJwtForToken(authHeader.slice('Bearer '.length));
  if (!jwt || isJwtExpired(jwt)) {
    send401(res, 'Unauthorized or token expired');
    return null;
  }
  return jwt;
}

interface McpSession {
  transport: StreamableHTTPServerTransport;
  principal: string;
}

export function principalOf(jwt: string): string {
  try {
    const payloadBase64 = jwt.split('.')[1];
    const payload = JSON.parse(Buffer.from(payloadBase64, 'base64url').toString('utf8')) as { sub?: unknown };
    if (typeof payload.sub === 'string' && payload.sub) return payload.sub;
  } catch {
    // fall through to JWT fingerprint
  }
  return jwt;
}

const defaultSessions = new Map<string, McpSession>();

function findOwnedSession(
  sessions: Map<string, McpSession>,
  req: Request,
  res: Response,
  jwt: string,
): StreamableHTTPServerTransport | null {
  const session = sessions.get(req.headers['mcp-session-id'] as string);
  if (!session) {
    res.status(400).send('Invalid or missing session ID');
    return null;
  }
  if (session.principal !== principalOf(jwt)) {
    send401(res, 'Unauthorized: session belongs to a different principal');
    return null;
  }
  return session.transport;
}

export function createMcpRouter(
  makeClient: MakeClient = defaultMakeClient,
  sessions: Map<string, McpSession> = defaultSessions,
): Router {
  const mcpRouter = Router();

  mcpRouter.post('/mcp', async (req: Request, res: Response) => {
    const jwt = requireBearer(req, res);
    if (!jwt) return;

    const sessionId = req.headers['mcp-session-id'] as string | undefined;
    const existing = sessionId ? sessions.get(sessionId) : undefined;

    if (existing && existing.principal !== principalOf(jwt)) {
      send401(res, 'Unauthorized: session belongs to a different principal');
      return;
    }
    let transport = existing?.transport;

    if (!transport) {
      if (sessionId || !isInitializeRequest(req.body)) {
        res.status(400).json({
          jsonrpc: '2.0',
          error: { code: -32000, message: 'Bad Request: No valid session ID provided' },
          id: null,
        });
        return;
      }
      const principal = principalOf(jwt);
      transport = new StreamableHTTPServerTransport({
        sessionIdGenerator: () => randomUUID(),
        onsessioninitialized: (sid) => {
          sessions.set(sid, { transport: transport!, principal });
        },
      });
      transport.onclose = () => {
        const sid = transport!.sessionId;
        if (sid) sessions.delete(sid);
      };
      const baseUrl = process.env.BUDGET_TRACKER_URL || 'http://localhost:3300';
      const server = createMcpServer(jwt, baseUrl, makeClient);
      await server.connect(transport);
    }

    await transport.handleRequest(req, res, req.body);
  });

  mcpRouter.get('/mcp', async (req: Request, res: Response) => {
    const jwt = requireBearer(req, res);
    if (!jwt) return;
    const transport = findOwnedSession(sessions, req, res, jwt);
    if (!transport) return;
    await transport.handleRequest(req, res);
  });

  mcpRouter.delete('/mcp', async (req: Request, res: Response) => {
    const jwt = requireBearer(req, res);
    if (!jwt) return;
    const transport = findOwnedSession(sessions, req, res, jwt);
    if (!transport) return;
    await transport.handleRequest(req, res);
  });

  return mcpRouter;
}

export const mcpRouter = createMcpRouter();

export function clearSessions(): void {
  defaultSessions.clear();
}
