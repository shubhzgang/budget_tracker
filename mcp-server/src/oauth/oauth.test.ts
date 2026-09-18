import { describe, it, expect, beforeEach } from 'vitest';
import request from 'supertest';
import { createHash, randomBytes } from 'node:crypto';
import { createApp } from '../index.js';
import { clearStores, registerClient, storeAuthCode } from './store.js';

const app = createApp();

beforeEach(() => clearStores());

function pkcePair() {
  const verifier = randomBytes(32).toString('base64url');
  const challenge = createHash('sha256').update(verifier).digest().toString('base64url');
  return { verifier, challenge };
}

describe('OAuth HTTP flow', () => {
  it('serves authorization server metadata', async () => {
    const res = await request(app).get('/.well-known/oauth-authorization-server');
    expect(res.status).toBe(200);
    expect(res.body.authorization_endpoint).toMatch(/\/authorize$/);
    expect(res.body.token_endpoint).toMatch(/\/token$/);
    expect(res.body.registration_endpoint).toMatch(/\/register$/);
    expect(res.body.code_challenge_methods_supported).toContain('S256');
  });

  it('serves protected resource metadata', async () => {
    const res = await request(app).get('/.well-known/oauth-protected-resource');
    expect(res.status).toBe(200);
    expect(res.body.resource).toMatch(/\/mcp$/);
    expect(res.body.authorization_servers).toHaveLength(1);
  });

  it('registers a client and rejects missing redirect_uris', async () => {
    const ok = await request(app)
      .post('/register')
      .send({ client_name: 'test', redirect_uris: ['http://localhost:9999/cb'] });
    expect(ok.status).toBe(201);
    expect(ok.body.client_id).toBeTruthy();

    const bad = await request(app).post('/register').send({ client_name: 'test' });
    expect(bad.status).toBe(400);
  });

  it('rejects authorize with unknown client or mismatched redirect', async () => {
    const res = await request(app).get('/authorize').query({
      response_type: 'code',
      client_id: 'unknown',
      redirect_uri: 'http://evil.example/cb',
      code_challenge: 'abc',
      code_challenge_method: 'S256',
    });
    expect(res.status).toBe(400);
  });

  it('serves login HTML with escaped values', async () => {
    const client = registerClient('web', ['http://localhost:9999/cb']);
    const res = await request(app).get('/authorize').query({
      response_type: 'code',
      client_id: client.clientId,
      redirect_uri: 'http://localhost:9999/cb',
      code_challenge: 'challenge123',
      code_challenge_method: 'S256',
      state: '"><b>evil</b>',
    });
    expect(res.status).toBe(200);
    expect(res.text).toContain('<form action="/authorize" method="POST">');
    expect(res.text).not.toContain('"><b>evil</b>');
    expect(res.text).toContain('&quot;&gt;&lt;b&gt;evil&lt;/b&gt;');
  });

  it('exchanges a valid code with PKCE and rejects reuse', async () => {
    const client = registerClient('web', ['http://localhost:9999/cb']);
    const { verifier, challenge } = pkcePair();
    const code = storeAuthCode({
      budgetTrackerJwt: 'jwt-test',
      codeChallenge: challenge,
      codeChallengeMethod: 'S256',
      redirectUri: 'http://localhost:9999/cb',
      clientId: client.clientId,
    });
    const ok = await request(app)
      .post('/token')
      .type('form')
      .send({
        grant_type: 'authorization_code',
        code,
        redirect_uri: 'http://localhost:9999/cb',
        client_id: client.clientId,
        code_verifier: verifier,
      });
    expect(ok.status).toBe(200);
    expect(ok.body.access_token).toBeTruthy();
    expect(ok.body.token_type).toBe('Bearer');

    const reuse = await request(app)
      .post('/token')
      .type('form')
      .send({
        grant_type: 'authorization_code',
        code,
        redirect_uri: 'http://localhost:9999/cb',
        client_id: client.clientId,
        code_verifier: verifier,
      });
    expect(reuse.status).toBe(400);
  });

  it('rejects token exchange with wrong verifier or wrong redirect', async () => {
    const client = registerClient('web', ['http://localhost:9999/cb']);
    const { challenge } = pkcePair();
    const code = storeAuthCode({
      budgetTrackerJwt: 'jwt-test',
      codeChallenge: challenge,
      codeChallengeMethod: 'S256',
      redirectUri: 'http://localhost:9999/cb',
      clientId: client.clientId,
    });
    const badVerifier = await request(app).post('/token').send({
      grant_type: 'authorization_code',
      code,
      redirect_uri: 'http://localhost:9999/cb',
      client_id: client.clientId,
      code_verifier: 'wrong',
    });
    expect(badVerifier.status).toBe(400);
    expect(badVerifier.body.error).toBe('invalid_grant');
  });
});
