import { describe, it, expect, afterEach } from 'vitest';
import request from 'supertest';
import { createApp } from './index.js';

describe('origin guard', () => {
  const saved = process.env.MCP_ALLOWED_ORIGINS;
  afterEach(() => {
    if (saved === undefined) delete process.env.MCP_ALLOWED_ORIGINS;
    else process.env.MCP_ALLOWED_ORIGINS = saved;
  });

  it('allows localhost by default and rejects other origins', async () => {
    delete process.env.MCP_ALLOWED_ORIGINS;
    const app = createApp();
    expect((await request(app).get('/health').set('Origin', 'http://localhost:3300')).status).toBe(200);
    expect((await request(app).get('/health').set('Origin', 'https://evil.example.com')).status).toBe(403);
  });

  it('allows extra origins from MCP_ALLOWED_ORIGINS without regex bypass', async () => {
    process.env.MCP_ALLOWED_ORIGINS = 'https://mcp.example.com, http://other.example:8080/';
    const app = createApp();
    expect((await request(app).get('/health').set('Origin', 'https://mcp.example.com')).status).toBe(200);
    expect((await request(app).get('/health').set('Origin', 'http://other.example:8080')).status).toBe(200);
    expect((await request(app).get('/health').set('Origin', 'https://mcpXexample.com')).status).toBe(403);
    expect((await request(app).get('/health').set('Origin', 'https://evil.example.com')).status).toBe(403);
    expect((await request(app).get('/health').set('Origin', 'http://localhost:3300')).status).toBe(200);
  });
});
