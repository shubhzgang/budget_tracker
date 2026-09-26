import { Router, type Request, type Response } from 'express';
import { getClient, storeAuthCode } from './store.js';

export function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

export function getBudgetTrackerUrl(): string {
  return (process.env.BUDGET_TRACKER_URL || 'http://localhost:3300').replace(/\/$/, '');
}

export const loginHtmlTemplate = (
  clientId: string,
  redirectUri: string,
  codeChallenge: string,
  codeChallengeMethod: string,
  state: string,
  error?: string,
) => `
<!DOCTYPE html>
<html>
<head><title>Budget Tracker — Sign In</title></head>
<body>
  <h2>Sign In</h2>
  ${error ? `<p style="color: red;">${escapeHtml(error)}</p>` : ''}
  <form action="/authorize" method="POST">
    <input type="hidden" name="client_id" value="${escapeHtml(clientId)}">
    <input type="hidden" name="redirect_uri" value="${escapeHtml(redirectUri)}">
    <input type="hidden" name="code_challenge" value="${escapeHtml(codeChallenge)}">
    <input type="hidden" name="code_challenge_method" value="${escapeHtml(codeChallengeMethod)}">
    <input type="hidden" name="state" value="${escapeHtml(state)}">
    <label>Email: <input type="email" name="email" required></label><br>
    <label>Password: <input type="password" name="password" required></label><br>
    <button type="submit">Sign In</button>
  </form>
</body>
</html>
`;

function validateClientAndRedirect(clientId: unknown, redirectUri: unknown): boolean {
  if (typeof clientId !== 'string' || typeof redirectUri !== 'string') return false;
  const client = getClient(clientId);
  if (!client) return false;
  return client.redirectUris.includes(redirectUri);
}

export async function loginToBudgetTracker(email: string, password: string): Promise<string> {
  const baseUrl = getBudgetTrackerUrl();
  const response = await fetch(`${baseUrl}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Login failed: ${response.status} ${text}`);
  }
  const data = (await response.json()) as { token?: string; accessToken?: string };
  const jwt = data.token ?? data.accessToken;
  if (!jwt) throw new Error('Login response did not contain a token');
  return jwt;
}

export const authorizeRouter = Router();

authorizeRouter.get('/authorize', (req: Request, res: Response) => {
  const { response_type, client_id, redirect_uri, code_challenge, code_challenge_method, state } =
    req.query as Record<string, unknown>;
  if (response_type !== 'code') {
    res.status(400).send('Unsupported response_type (expected code)');
    return;
  }
  if (!validateClientAndRedirect(client_id, redirect_uri)) {
    res.status(400).send('Invalid client_id or redirect_uri');
    return;
  }
  if (typeof code_challenge !== 'string' || !code_challenge) {
    res.status(400).send('code_challenge is required');
    return;
  }
  res.send(
    loginHtmlTemplate(
      client_id as string,
      redirect_uri as string,
      code_challenge,
      (code_challenge_method as string) || 'S256',
      (state as string) || '',
    ),
  );
});

authorizeRouter.post('/authorize', async (req: Request, res: Response) => {
  const body = req.body ?? {};
  const { email, password, client_id, redirect_uri, code_challenge, code_challenge_method, state } = body;
  if (!validateClientAndRedirect(client_id, redirect_uri)) {
    res.status(400).send('Invalid client_id or redirect_uri');
    return;
  }
  if (typeof code_challenge !== 'string' || !code_challenge) {
    res.status(400).send('code_challenge is required');
    return;
  }
  try {
    const jwt = await loginToBudgetTracker(String(email ?? ''), String(password ?? ''));
    const code = storeAuthCode({
      budgetTrackerJwt: jwt,
      codeChallenge: code_challenge,
      codeChallengeMethod: typeof code_challenge_method === 'string' ? code_challenge_method : 'S256',
      redirectUri: String(redirect_uri),
      clientId: String(client_id),
    });
    const target = new URL(String(redirect_uri));
    target.searchParams.set('code', code);
    if (typeof state === 'string' && state) target.searchParams.set('state', state);
    res.redirect(302, target.toString());
  } catch {
    res.status(401).send(
      loginHtmlTemplate(
        String(client_id),
        String(redirect_uri),
        String(code_challenge),
        typeof code_challenge_method === 'string' ? code_challenge_method : 'S256',
        typeof state === 'string' ? state : '',
        'Invalid email or password',
      ),
    );
  }
});
