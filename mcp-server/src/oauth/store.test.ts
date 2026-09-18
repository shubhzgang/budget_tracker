import { describe, it, expect, beforeEach } from 'vitest';
import {
  registerClient,
  getClient,
  storeAuthCode,
  consumeAuthCode,
  storeToken,
  getJwtForToken,
  authorizationCodes,
  clearStores,
} from './store.js';

beforeEach(() => clearStores());

describe('oauth store', () => {
  it('registers and retrieves clients', () => {
    const client = registerClient('test', ['http://localhost:9999/cb']);
    expect(client.clientId).toBeTruthy();
    expect(getClient(client.clientId)?.redirectUris).toEqual(['http://localhost:9999/cb']);
  });

  it('consumes auth codes only once', () => {
    const code = storeAuthCode({
      budgetTrackerJwt: 'jwt-123',
      codeChallenge: 'challenge',
      codeChallengeMethod: 'S256',
      redirectUri: 'http://localhost:9999/cb',
      clientId: 'client-1',
    });
    expect(consumeAuthCode(code)?.budgetTrackerJwt).toBe('jwt-123');
    expect(consumeAuthCode(code)).toBeUndefined();
  });

  it('rejects expired auth codes', () => {
    const code = storeAuthCode({
      budgetTrackerJwt: 'jwt-123',
      codeChallenge: 'challenge',
      codeChallengeMethod: 'S256',
      redirectUri: 'http://localhost:9999/cb',
      clientId: 'client-1',
    });
    const stored = authorizationCodes.get(code)!;
    stored.expiresAt = new Date(Date.now() - 1000);
    expect(consumeAuthCode(code)).toBeUndefined();
  });

  it('round-trips access tokens to JWTs', () => {
    const token = storeToken('jwt-abc');
    expect(getJwtForToken(token)).toBe('jwt-abc');
    expect(getJwtForToken('unknown')).toBeUndefined();
  });
});
