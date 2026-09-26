import { Router, type Request, type Response } from 'express';

export function getBaseUrl(): string {
  return (process.env.MCP_BASE_URL || 'http://localhost:3002').replace(/\/$/, '');
}

export function send401(res: Response, message: string): void {
  const baseUrl = getBaseUrl();
  res.setHeader(
    'WWW-Authenticate',
    `Bearer resource_metadata="${baseUrl}/.well-known/oauth-protected-resource"`,
  );
  res.status(401).json({ error: 'unauthorized', error_description: message });
}

export const metadataRouter = Router();

metadataRouter.get('/.well-known/oauth-authorization-server', (_req: Request, res: Response) => {
  const baseUrl = getBaseUrl();
  res.json({
    issuer: baseUrl,
    authorization_endpoint: `${baseUrl}/authorize`,
    token_endpoint: `${baseUrl}/token`,
    registration_endpoint: `${baseUrl}/register`,
    response_types_supported: ['code'],
    grant_types_supported: ['authorization_code'],
    code_challenge_methods_supported: ['S256'],
    token_endpoint_auth_methods_supported: ['none'],
    scopes_supported: ['mcp:tools'],
  });
});

metadataRouter.get('/.well-known/oauth-protected-resource', (_req: Request, res: Response) => {
  const baseUrl = getBaseUrl();
  res.json({
    resource: `${baseUrl}/mcp`,
    authorization_servers: [baseUrl],
    scopes_supported: ['mcp:tools'],
    bearer_methods_supported: ['header'],
  });
});
