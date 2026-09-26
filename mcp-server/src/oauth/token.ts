import { createHash } from 'node:crypto';
import { Router, type Request, type Response } from 'express';
import { consumeAuthCode, storeToken } from './store.js';

export function verifyPkce(codeVerifier: string, codeChallenge: string): boolean {
  const hash = createHash('sha256').update(codeVerifier).digest();
  return hash.toString('base64url') === codeChallenge;
}

export const tokenRouter = Router();

tokenRouter.post('/token', (req: Request, res: Response) => {
  const body = req.body ?? {};
  const grantType = body.grant_type;
  const code = body.code;
  const redirectUri = body.redirect_uri;
  const clientId = body.client_id;
  const codeVerifier = body.code_verifier;

  if (grantType !== 'authorization_code') {
    res.status(400).json({ error: 'unsupported_grant_type' });
    return;
  }
  if (typeof code !== 'string' || !code) {
    res.status(400).json({ error: 'invalid_grant' });
    return;
  }
  const stored = consumeAuthCode(code);
  if (!stored) {
    res.status(400).json({ error: 'invalid_grant' });
    return;
  }
  if (typeof redirectUri !== 'string' || redirectUri !== stored.redirectUri) {
    res.status(400).json({ error: 'invalid_grant' });
    return;
  }
  if (typeof clientId !== 'string' || clientId !== stored.clientId) {
    res.status(400).json({ error: 'invalid_grant' });
    return;
  }
  if (typeof codeVerifier !== 'string' || !verifyPkce(codeVerifier, stored.codeChallenge)) {
    res.status(400).json({ error: 'invalid_grant' });
    return;
  }
  const accessToken = storeToken(stored.budgetTrackerJwt);
  res.json({ access_token: accessToken, token_type: 'Bearer', expires_in: 86400 });
});
