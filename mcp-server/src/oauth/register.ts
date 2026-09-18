import { Router, type Request, type Response } from 'express';
import { registerClient } from './store.js';

export const registerRouter = Router();

registerRouter.post('/register', (req: Request, res: Response) => {
  const body = req.body ?? {};
  const clientName = typeof body.client_name === 'string' ? body.client_name : undefined;
  const redirectUris = Array.isArray(body.redirect_uris)
    ? body.redirect_uris.filter((u: unknown): u is string => typeof u === 'string')
    : [];
  if (redirectUris.length === 0) {
    res.status(400).json({ error: 'invalid_client_metadata', error_description: 'redirect_uris is required' });
    return;
  }
  const client = registerClient(clientName, redirectUris);
  res.status(201).json({
    client_id: client.clientId,
    client_name: client.clientName,
    redirect_uris: client.redirectUris,
    grant_types: ['authorization_code'],
    response_types: ['code'],
    token_endpoint_auth_method: 'none',
  });
});
