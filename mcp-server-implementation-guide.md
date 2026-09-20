# Budget Tracker MCP Server — Implementation Guide

> **Audience**: Junior developer. This guide walks you through building an MCP (Model Context Protocol) server for the Budget Tracker app. Follow the steps in order.

---

## Table of Contents

1. [What You're Building](#1-what-youre-building)
2. [Prerequisites](#2-prerequisites)
3. [Architecture Overview](#3-architecture-overview)
4. [Budget Tracker API Reference](#4-budget-tracker-api-reference)
5. [Step-by-Step Implementation](#5-step-by-step-implementation)
   - [Step 1: Project Scaffolding](#step-1-project-scaffolding)
   - [Step 2: OAuth In-Memory Store](#step-2-oauth-in-memory-store)
   - [Step 3: OAuth Metadata Endpoint](#step-3-oauth-metadata-endpoint)
   - [Step 4: Dynamic Client Registration](#step-4-dynamic-client-registration)
   - [Step 5: Authorization Endpoint + Login Page](#step-5-authorization-endpoint--login-page)
   - [Step 6: Token Endpoint](#step-6-token-endpoint)
   - [Step 7: Budget Tracker API Client](#step-7-budget-tracker-api-client)
   - [Step 8: MCP Tool Definitions](#step-8-mcp-tool-definitions)
   - [Step 9: MCP Server Setup](#step-9-mcp-server-setup)
   - [Step 10: Streamable HTTP Handler](#step-10-streamable-http-handler)
   - [Step 11: Express Entry Point](#step-11-express-entry-point)
   - [Step 12: Docker & Makefile Integration](#step-12-docker--makefile-integration)
   - [Step 13: Unit & Component Tests](#step-13-unit--component-tests)
   - [Step 14: Demo-Data Smoke Test & make test-mcp](#step-14-demo-data-smoke-test--make-test-mcp)
6. [Task Checklist](#6-task-checklist)
7. [Testing Guide](#7-testing-guide)

---

## 1. What You're Building

An **MCP server** that lets AI assistants (Claude Desktop, Cursor, etc.) interact with the Budget Tracker app. Think of it as an API adapter: the AI calls MCP tools like `create_transaction` or `get_expenditure_summary`, and the MCP server translates those into REST API calls to the Budget Tracker backend.

**Key features:**
- **Streamable HTTP transport**: The server is a remote HTTP service. Users configure it by entering a URL (e.g., `http://localhost:3001/mcp`) — that's it.
- **OAuth 2.1 authentication with PKCE**: When an AI client connects for the first time, a browser opens with a login form. The user enters their Budget Tracker email + password. After that, everything is automatic.
- **19 tools** covering accounts, transactions, transfers, activity feed, expenditure summary, categories, and labels (accounts and labels are read-only — no create/update/delete tools for them).

---

## 2. Prerequisites

- **Node.js 22+** and **npm**
- **TypeScript** basics (types, interfaces, async/await)
- The Budget Tracker app running locally (`make run-demo` — accessible at `http://localhost:3300`)
- Test credentials: `test@example.com` / `password` (from demo mode)

**Key npm packages you'll use:**
| Package | What it does |
|---------|-------------|
| `@modelcontextprotocol/sdk` | Official MCP SDK — provides `McpServer`, `StreamableHTTPServerTransport` |
| `express` | HTTP server framework |
| `zod` | Schema validation for tool inputs |
| `uuid` | Generate unique IDs |

---

## 3. Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│  MCP Client (AI IDE like Claude Desktop/Cursor)             │
│                                                             │
│  Config: serverUrl = "http://localhost:3001/mcp"            │
└──────┬──────────────────────────────────────────────────────┘
       │
       │ 1. POST /mcp → 401 Unauthorized
       │ 2. GET /.well-known/oauth-authorization-server → OAuth metadata
       │ 3. POST /register → client_id (Dynamic Client Registration)
       │ 4. Browser opens → GET /authorize → login form
       │ 5. User submits email+password → POST /authorize → redirect with code
       │ 6. POST /token → exchange code for access token
       │ 7. POST /mcp + Authorization: Bearer <token> → tool calls work!
       │
       ▼
┌──────────────────────────────────────────────────┐
│  MCP Server (Express.js — port 3001)             │
│                                                  │
│  OAuth endpoints:                                │
│    /.well-known/oauth-authorization-server       │
│    /register                                     │
│    /authorize (GET=login page, POST=validate)    │
│    /token                                        │
│                                                  │
│  MCP endpoint:                                   │
│    /mcp (POST=JSON-RPC, GET=SSE, DELETE=close)   │
│                                                  │
│  Internal: calls Budget Tracker REST API         │
└──────┬───────────────────────────────────────────┘
       │
       │ HTTP with Bearer JWT token
       ▼
┌──────────────────────────────────────────────────┐
│  Budget Tracker (Spring Boot — port 3300)        │
│  REST API at /api/v1/*                           │
└──────────────────────────────────────────────────┘
```

### How OAuth Works Here (simplified)

The MCP spec requires OAuth 2.1 for remote servers. Since Budget Tracker uses simple email+password → JWT auth (not OAuth natively), our MCP server acts as a **thin OAuth wrapper**:

1. MCP client connects → server says "you need auth" (401 with `WWW-Authenticate` pointing at `/.well-known/oauth-protected-resource`)
2. MCP client reads the Protected Resource Metadata → finds our authorization server → loads its OAuth metadata from `/.well-known/oauth-authorization-server`
3. MCP client opens a browser → our custom login page at `/authorize`
4. User enters Budget Tracker credentials → we call Budget Tracker's `/api/v1/auth/login` → get JWT
5. We generate an OAuth authorization code → redirect browser back to MCP client
6. MCP client exchanges code for an access token at `/token`
7. Internally, that access token maps to the Budget Tracker JWT
8. On every tool call, we use that JWT to call Budget Tracker's REST API

### File Structure

```
mcp-server/
├── package.json
├── tsconfig.json
├── Dockerfile
├── .gitignore
└── src/
    ├── index.ts                  # Express app entry point
    ├── oauth/
    │   ├── store.ts              # In-memory stores (clients, codes, tokens)
    │   ├── metadata.ts           # GET /.well-known/oauth-authorization-server + /.well-known/oauth-protected-resource
    │   ├── register.ts           # POST /register
    │   ├── authorize.ts          # GET+POST /authorize (login HTML embedded as a string template)
    │   └── token.ts              # POST /token
    ├── api/
    │   └── client.ts             # Budget Tracker REST API client
    ├── mcp/
    │   ├── server.ts             # McpServer + tool registration
    │   └── handler.ts            # Streamable HTTP transport handler
    ├── tools/
    │   ├── accounts.ts
    │   ├── transactions.ts
    │   ├── transfers.ts
    │   ├── activity.ts
    │   ├── categories.ts
    │   └── labels.ts
    └── test/
        └── tool-smoke.ts         # Demo-data smoke: OAuth flow + tool-call assertions (Step 14)
```

Unit/component tests (Step 13) live next to their sources as `src/**/*.test.ts` — they are excluded from the build by `tsconfig`.

---

## 4. Budget Tracker API Reference

> **Base URL**: `http://localhost:3300` (or `http://backend:8080` inside Docker)
>
> **Auth**: All endpoints below (except login) require `Authorization: Bearer <jwt_token>` header.

### 4.1 Authentication

#### Login
```
POST /api/v1/auth/login
Content-Type: application/json

Request:  { "email": "test@example.com", "password": "password" }
Response: { "token": "eyJhb...", "type": "Bearer", "id": "uuid", "email": "test@example.com" }
```

### 4.2 Accounts

```
GET    /api/v1/accounts              → Account[]
GET    /api/v1/accounts/:id          → Account
POST   /api/v1/accounts              → Account     (body: Account)
PUT    /api/v1/accounts/:id          → Account     (body: Account)
DELETE /api/v1/accounts/:id          → 204
```

> **Note:** MCP exposes accounts **read-only** (`list_accounts`, `get_account`) — the write endpoints above exist on the backend but no MCP tool calls them (deleting an account would cascade-delete its transactions and transfers).

**Account shape:**
```json
{
  "id": "uuid",
  "name": "Savings",
  "type": "BANK",              // BANK | CREDIT_CARD | CASH | FRIEND_LENDING
  "initialBalance": 1000.00,
  "balance": 1500.00,
  "creditLimit": null,         // only for CREDIT_CARD
  "userId": "uuid",
  "createdAt": "2026-09-17T12:00:00Z"
}
```

### 4.3 Transactions

```
GET    /api/v1/transactions          → Page<Transaction>
       ?search=groceries             (optional, searches description/category/labels)
       &type=EXPENSE                 (optional: INCOME | EXPENSE | LEND | BORROW)
       &startDate=2026-09-01T00:00:00Z  (optional, ISO 8601)
       &endDate=2026-09-30T23:59:59Z    (optional)
       &page=0&size=20&sort=transactionDate,desc  (pagination)

GET    /api/v1/transactions/:id      → Transaction
POST   /api/v1/transactions          → Transaction  (body: TransactionRequest)
PUT    /api/v1/transactions/:id      → Transaction  (body: TransactionRequest)
DELETE /api/v1/transactions/:id      → 204
```

**TransactionRequest body:**
```json
{
  "accountId": "uuid",          // required
  "amount": 50.00,              // required, > 0
  "type": "EXPENSE",            // required: INCOME | EXPENSE | LEND | BORROW
  "transactionDate": "2026-09-17T12:00:00+05:30",  // required, ISO 8601
  "description": "Groceries",   // optional
  "categoryId": "uuid",         // optional
  "labelIds": ["uuid", "uuid"]  // optional
}
```

### 4.4 Expenditure Summary

```
GET /api/v1/transactions/expenditure-summary → ExpenditureSummaryResponse
```

**Response:**
```json
{
  "yesterday": 0.00,
  "today": 50.00,
  "lastWeek": 200.00,
  "thisWeek": 150.00,
  "lastMonth": 800.00,
  "thisMonth": 600.00,
  "yesterdayByLabel": [{ "labelName": "NEEDS", "amount": 10.00 }],
  "todayByLabel": [{ "labelName": "WANTS", "amount": 50.00 }],
  "lastWeekByLabel": [],
  "thisWeekByLabel": [],
  "lastMonthByLabel": [],
  "thisMonthByLabel": []
}
```

### 4.5 Transfers

```
GET    /api/v1/transfers             → Page<TransferResponse>
       ?search=...&startDate=...&endDate=...&page=0&size=20

GET    /api/v1/transfers/:id         → TransferResponse
POST   /api/v1/transfers             → TransferResponse  (body: TransferRequest)
PUT    /api/v1/transfers/:id         → TransferResponse  (body: TransferRequest)
DELETE /api/v1/transfers/:id         → 204
```

**TransferRequest body:**
```json
{
  "fromAccountId": "uuid",         // required
  "toAccountId": "uuid",           // required, must differ from fromAccountId
  "fromAmount": 100.00,            // exactly 2 of 3 (fromAmount, toAmount, adjustment) must be set
  "toAmount": 95.00,               //   the 3rd is auto-computed: adjustment = toAmount - fromAmount
  "adjustment": null,
  "transactionDate": "2026-09-17T12:00:00+05:30",  // required
  "description": "Transfer to friend",              // required
  "categoryId": "uuid",            // optional
  "labelIds": ["uuid"]             // optional
}
```

### 4.6 Activity Feed (unified transactions + transfers)

```
GET /api/v1/activity               → Page<ActivityResponse>
    ?search=...
    &type=EXPENSE                  // INCOME | EXPENSE | LEND | BORROW | TRANSFER
    &accountId=uuid
    &startDate=...&endDate=...
    &page=0&size=20
```

**ActivityResponse:**
```json
{
  "id": "uuid",
  "kind": "TRANSACTION",         // or "TRANSFER"
  "type": "EXPENSE",             // or "TRANSFER", "INCOME", etc.
  "amount": 50.00,               // for TRANSACTION kind
  "fromAmount": null,            // for TRANSFER kind
  "toAmount": null,
  "adjustment": null,
  "description": "Groceries",
  "transactionDate": "2026-09-17T10:00:00Z",
  "account": { ... },
  "toAccount": null,             // non-null for TRANSFER
  "category": { ... },
  "labels": [{ "id": "uuid", "name": "NEEDS" }],
  "createdAt": "2026-09-17T10:00:00Z"
}
```

### 4.7 Categories

```
GET    /api/v1/categories            → Category[]
POST   /api/v1/categories            → Category  (body: { "name": "Food", "icon": "🍔" })
PUT    /api/v1/categories/:id        → Category  (body: { "name": ..., "icon": ... } — full replace: omitted icon erases the emoji; duplicate name → 400)
DELETE /api/v1/categories/:id        → 204       (any category, incl. defaults/in-use — transactions are detached via ON DELETE SET NULL)
```

### 4.8 Labels

```
GET    /api/v1/labels                → Label[]
POST   /api/v1/labels                → Label     (body: { "name": "NEEDS" }; name may not contain '|')
PUT    /api/v1/labels/:id            → Label     (rename; recomputes dashboard totals)
DELETE /api/v1/labels/:id            → 204       (works on ALL labels incl. defaults; recomputes dashboard totals)
```

> **Note:** MCP exposes labels **read-only** (`list_labels`) — the write endpoints exist on the backend but no MCP tool calls them.

### 4.9 Pagination (Spring Data format)

All paginated endpoints return:
```json
{
  "content": [ ... ],           // array of items
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,                  // current page (0-indexed)
  "size": 20,
  "first": true,
  "last": false
}
```

---

## 5. Step-by-Step Implementation

### Step 1: Project Scaffolding

Create `mcp-server/` directory at the project root.

**`mcp-server/package.json`**
```json
{
  "name": "budget-tracker-mcp-server",
  "version": "1.0.0",
  "description": "MCP server for Budget Tracker",
  "type": "module",
  "main": "dist/index.js",
  "scripts": {
    "build": "tsc",
    "start": "node dist/index.js",
    "dev": "tsc --watch & node --watch dist/index.js",
    "test": "vitest run",
    "smoke": "tsx src/test/tool-smoke.ts"
  },
  "dependencies": {
    "@modelcontextprotocol/sdk": "^1.30.0",
    "express": "^5.1.0",
    "uuid": "^11.1.0",
    "zod": "^3.25.0"
  },
  "devDependencies": {
    "@types/express": "^5.0.0",
    "@types/node": "^22.0.0",
    "@types/supertest": "^6.0.3",
    "supertest": "^7.1.0",
    "tsx": "^4.19.0",
    "typescript": "^5.8.0",
    "vitest": "^3.2.0"
  }
}
```

> **Why `^1.30.0` and not an older pin:** `@modelcontextprotocol/sdk` publishes on the 1.x line, and the Node.js `StreamableHTTPServerTransport` was rebuilt mid-line (Web-Standard wrapper via `@hono/node-server`). On current 1.x the transport exposes **a single method `handleRequest(req, res, parsedBody?)`** — the older `handlePostMessage` / `handleGetMessage` / `handleDeleteMessage` methods **no longer exist**. Step 10 below is written for this current API. Also note the SDK peer-dependency on `zod` is `^3.25 || ^4.0`, so the zod pin above is fine.

**`mcp-server/tsconfig.json`**
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "outDir": "dist",
    "rootDir": "src",
    "strict": true,
    "esModuleInterop": true,
    "resolveJsonModule": true,
    "declaration": true,
    "sourceMap": true
  },
  "exclude": ["src/**/*.test.ts", "src/test/**"],
  "include": ["src/**/*"]
}
```

**`mcp-server/.gitignore`**
```
node_modules/
dist/
```

**Run**: `cd mcp-server && npm install`

---

### Step 2: OAuth In-Memory Store

**File: `mcp-server/src/oauth/store.ts`**

This module holds all OAuth state in memory. In production you'd use a database, but for a personal project, in-memory is fine.

**What to implement:**
- `registeredClients: Map<string, ClientInfo>` — stores dynamically registered clients
- `authorizationCodes: Map<string, AuthCodeData>` — stores pending auth codes (code → { budgetTrackerJwt, codeChallenge, redirectUri, clientId, expiresAt })
- `accessTokens: Map<string, string>` — maps MCP access token → Budget Tracker JWT
- Helper functions: `registerClient()`, `storeAuthCode()`, `consumeAuthCode()`, `storeToken()`, `getJwtForToken()`
- Auth codes should expire after 5 minutes. Access tokens can last 24 hours (matching the Budget Tracker JWT TTL).

**Types to define:**
```typescript
interface ClientInfo {
  clientId: string;
  clientName?: string;
  redirectUris: string[];
  registeredAt: Date;
}

interface AuthCodeData {
  budgetTrackerJwt: string;
  codeChallenge: string;
  codeChallengeMethod: string; // always "S256"
  redirectUri: string;
  clientId: string;
  expiresAt: Date;
}
```

---

### Step 3: OAuth Metadata Endpoint

**File: `mcp-server/src/oauth/metadata.ts`**

Create an Express router that handles:

```
GET /.well-known/oauth-authorization-server
```

Return JSON:
```json
{
  "issuer": "http://localhost:3001",
  "authorization_endpoint": "http://localhost:3001/authorize",
  "token_endpoint": "http://localhost:3001/token",
  "registration_endpoint": "http://localhost:3001/register",
  "response_types_supported": ["code"],
  "grant_types_supported": ["authorization_code"],
  "code_challenge_methods_supported": ["S256"],
  "token_endpoint_auth_methods_supported": ["none"],
  "scopes_supported": ["mcp:tools"]
}
```

> **Important:** The URLs must match your server's actual host/port. Read from an environment variable like `MCP_BASE_URL` (default: `http://localhost:3001`).

**Also serve Protected Resource Metadata (MUST per MCP spec, RFC 9728).** This same file adds a second route:

```
GET /.well-known/oauth-protected-resource
```

Return JSON:
```json
{
  "resource": "http://localhost:3001/mcp",
  "authorization_servers": ["http://localhost:3001"],
  "scopes_supported": ["mcp:tools"],
  "bearer_methods_supported": ["header"]
}
```

(Both values are derived from `MCP_BASE_URL`: `resource` = `${MCP_BASE_URL}/mcp`, `authorization_servers` = `[MCP_BASE_URL]`.)

**And every 401 from the `/mcp` endpoint must point here (MUST per spec).** Clients discover auth *through the error response*: a bare `401` body is not enough — spec-compliant clients only auto-launch the OAuth flow when the response carries:

```
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer resource_metadata="http://localhost:3001/.well-known/oauth-protected-resource"
```

Write a small `send401(res, message)` helper that sets this header (built from `MCP_BASE_URL`) and returns the 401 — reuse it in Steps 5, 6, and 10. Without this header, clients like Claude/Cursor will not re-authenticate, and the "OAuth flow triggers automatically" behaviour described in the Testing Guide will not happen.

---

### Step 4: Dynamic Client Registration

**File: `mcp-server/src/oauth/register.ts`**

Create an Express router that handles:

```
POST /register
Content-Type: application/json
```

**Request body** (from MCP client, per RFC 7591):
```json
{
  "client_name": "My AI Client",
  "redirect_uris": ["http://localhost:12345/callback"],
  "grant_types": ["authorization_code"],
  "response_types": ["code"],
  "token_endpoint_auth_method": "none"
}
```

**What to do:**
1. Generate a random `client_id` (use `uuid`).
2. Store in the clients map via `registerClient()`.
3. Return:
```json
{
  "client_id": "generated-uuid",
  "client_name": "My AI Client",
  "redirect_uris": ["http://localhost:12345/callback"],
  "grant_types": ["authorization_code"],
  "response_types": ["code"],
  "token_endpoint_auth_method": "none"
}
```

> **Tip**: Be permissive — accept any client. This is a personal project.

---

### Step 5: Authorization Endpoint + Login Page

**File: `mcp-server/src/oauth/authorize.ts`**

This is the most complex part. Two routes:

#### `GET /authorize` — Serve the login page

The MCP client redirects the user's browser here with query params:
```
GET /authorize?
  response_type=code&
  client_id=xxx&
  redirect_uri=http://localhost:12345/callback&
  code_challenge=abc123&
  code_challenge_method=S256&
  state=random-state-string
```

**What to do:**
1. Validate that `client_id` exists in your registered clients AND that `redirect_uri` **exactly matches** one of that client's stored `redirect_uris` (from Step 4). If either fails → `400`, do NOT render the form. This is the guard that stops an attacker from registering a client and then pointing the login page at a foreign redirect target (open-redirect → auth-code theft). "Be permissive" applies to *who* may register, never to where codes are redirected.
2. Note that spec-compliant clients also send a `resource` parameter (RFC 8707, the canonical URL of your MCP server). Accept and ignore it — strict resource binding is out of scope for a personal server, but **do not reject** requests that include it.
3. Serve an HTML login form. The form must include hidden fields for all the OAuth params (client_id, redirect_uri, code_challenge, code_challenge_method, state).
4. The form has email + password fields and a Submit button.
5. The form POSTs to `/authorize`.

#### `POST /authorize` — Validate credentials and redirect

**What to do:**
1. Extract email, password, and all hidden OAuth fields from the form body.
2. **Re-validate** `client_id` + `redirect_uri` exactly as in `GET /authorize` (hidden fields are attacker-tamperable — never trust them). If invalid → `400`. Also accept (and ignore) a `resource` field if present.
3. Call Budget Tracker's login API:
   ```
   POST http://localhost:3300/api/v1/auth/login
   Content-Type: application/json
   Body: { "email": "...", "password": "..." }
   ```
4. If login fails → re-render login page with an error message.
5. If login succeeds → you get a JWT token from the response.
6. Generate a random authorization code (use `uuid`).
7. Store it: `storeAuthCode(code, { budgetTrackerJwt, codeChallenge, redirectUri, clientId })`.
8. Redirect the browser to: `{redirect_uri}?code={code}&state={state}`.

**How to serve the HTML**: Instead of a separate file, embed the HTML as a string template directly in `authorize.ts` and return it. This avoids complex build steps with copying HTML files into `dist/`. 

**Escape everything you interpolate.** Every one of these values comes from the URL/form and is attacker-controlled. A page that harvests passwords must never reflect raw input into HTML (a `"` in `redirect_uri` or `state` would let an attacker break out of the attribute and inject script). Always pass values through an escaping helper:

```typescript
function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

const loginHtmlTemplate = (clientId: string, redirectUri: string, codeChallenge: string, codeChallengeMethod: string, state: string, error?: string) => `
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
```

---

### Step 6: Token Endpoint

**File: `mcp-server/src/oauth/token.ts`**

```
POST /token
Content-Type: application/x-www-form-urlencoded   (or application/json — support both)
```

**Request body:**
```
grant_type=authorization_code&
code=the-auth-code&
redirect_uri=http://localhost:12345/callback&
client_id=xxx&
code_verifier=the-original-pkce-verifier
```

**What to do:**
1. Look up the authorization code in your store via `consumeAuthCode(code)`. Spec-compliant clients also send a `resource` parameter here (RFC 8707) — accept and ignore it.
2. If not found or expired → return `400 { "error": "invalid_grant" }`.
3. Verify the submitted `redirect_uri` and `client_id` **exactly match** the values stored with the code — a code is only spendable by the same client, to the same redirect target, that obtained it. Mismatch → `400 { "error": "invalid_grant" }`.
4. Verify PKCE: compute `SHA256(code_verifier)` → base64url-encode it → compare with stored `code_challenge`. They must match.
5. If PKCE fails → return `400 { "error": "invalid_grant" }`.
6. Generate a random access token (use `uuid`).
7. Store the mapping: `storeToken(accessToken, budgetTrackerJwt)`.
8. Return:
```json
{
  "access_token": "generated-access-token",
  "token_type": "Bearer",
  "expires_in": 86400
}
```

**PKCE verification code** (this is the tricky part):
```typescript
import { createHash } from 'node:crypto';

function verifyPkce(codeVerifier: string, codeChallenge: string): boolean {
  const hash = createHash('sha256').update(codeVerifier).digest();
  const computed = hash.toString('base64url');
  return computed === codeChallenge;
}
```

---

### Step 7: Budget Tracker API Client

**File: `mcp-server/src/api/client.ts`**

A class that wraps all Budget Tracker REST API calls.

```typescript
class BudgetTrackerClient {
  constructor(
    private baseUrl: string,    // e.g. "http://localhost:3300"
    private jwtToken: string
  ) {}

  private async request(method: string, path: string, body?: unknown): Promise<any> {
    const url = `${this.baseUrl}${path}`;
    const options: RequestInit = {
      method,
      headers: {
        'Authorization': `Bearer ${this.jwtToken}`,
        'Content-Type': 'application/json',
      },
    };
    if (body) options.body = JSON.stringify(body);

    const response = await fetch(url, options);
    if (!response.ok) {
      const text = await response.text();
      throw new Error(`API error ${response.status}: ${text}`);
    }
    if (response.status === 204) return null;
    return response.json();
  }

  // Accounts
  async listAccounts() { return this.request('GET', '/api/v1/accounts'); }
  async getAccount(id: string) { return this.request('GET', `/api/v1/accounts/${id}`); }

  // Transactions
  async listTransactions(params: Record<string, string>) {
    const qs = new URLSearchParams(params).toString();
    return this.request('GET', `/api/v1/transactions?${qs}`);
  }
  async getTransaction(id: string) { return this.request('GET', `/api/v1/transactions/${id}`); }
  async createTransaction(data: any) { return this.request('POST', '/api/v1/transactions', data); }
  async updateTransaction(id: string, data: any) { return this.request('PUT', `/api/v1/transactions/${id}`, data); }
  async deleteTransaction(id: string) { return this.request('DELETE', `/api/v1/transactions/${id}`); }

  // Expenditure Summary
  async getExpenditureSummary() { return this.request('GET', '/api/v1/transactions/expenditure-summary'); }

  // Transfers
  async listTransfers(params: Record<string, string>) {
    const qs = new URLSearchParams(params).toString();
    return this.request('GET', `/api/v1/transfers?${qs}`);
  }
  async getTransfer(id: string) { return this.request('GET', `/api/v1/transfers/${id}`); }
  async createTransfer(data: any) { return this.request('POST', '/api/v1/transfers', data); }
  async updateTransfer(id: string, data: any) { return this.request('PUT', `/api/v1/transfers/${id}`, data); }
  async deleteTransfer(id: string) { return this.request('DELETE', `/api/v1/transfers/${id}`); }

  // Activity
  async getActivity(params: Record<string, string>) {
    const qs = new URLSearchParams(params).toString();
    return this.request('GET', `/api/v1/activity?${qs}`);
  }

  // Categories
  async listCategories() { return this.request('GET', '/api/v1/categories'); }
  async createCategory(data: any) { return this.request('POST', '/api/v1/categories', data); }
  async updateCategory(id: string, data: any) { return this.request('PUT', `/api/v1/categories/${id}`, data); }
  async deleteCategory(id: string) { return this.request('DELETE', `/api/v1/categories/${id}`); }

  // Labels
  async listLabels() { return this.request('GET', '/api/v1/labels'); }
}
```

---

### Step 8: MCP Tool Definitions

Create one file per domain. Each file exports a function that registers tools on the `McpServer`.

**Pattern for each tool:**
```typescript
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { BudgetTrackerClient } from '../api/client.js';

export function registerAccountTools(
  server: McpServer,
  jwt: string,
  baseUrl: string
) {
  const client = new BudgetTrackerClient(baseUrl, jwt);

  server.registerTool(
    'list_accounts',
    {
      description: 'List all accounts with their balances',
      inputSchema: {},  // no input params
    },
    async () => {
      const accounts = await client.listAccounts();
      return { content: [{ type: 'text', text: JSON.stringify(accounts, null, 2) }] };
    }
  );

  // ... more tools
}
```

> **Note**: Current 1.x SDKs also still accept the older `server.tool(name, description, schema, handler)` call, but it is deprecated — `registerTool` (above) is the supported form.

> **Important**: Notice how we pass `jwt` and `baseUrl` directly to the registration function. This allows the tools to securely close over the authenticated client without needing to parse headers inside the tool itself.

Here are all the tools to implement per file:

#### `mcp-server/src/tools/accounts.ts` — 2 tools (read-only)

| Tool Name | Input Schema (Zod) | API Call |
|-----------|-------------------|----------|
| `list_accounts` | `{}` (none) | `GET /api/v1/accounts` |
| `get_account` | `{ id: z.string().uuid() }` | `GET /api/v1/accounts/:id` |

#### `mcp-server/src/tools/transactions.ts` — 6 tools

| Tool Name | Input Schema | API Call |
|-----------|-------------|----------|
| `list_transactions` | `{ search: z.string().optional(), type: z.enum(["INCOME","EXPENSE","LEND","BORROW"]).optional(), startDate: z.string().optional(), endDate: z.string().optional(), page: z.number().optional().default(0), size: z.number().optional().default(20) }` | `GET /api/v1/transactions` |
| `get_transaction` | `{ id: z.string().uuid() }` | `GET /api/v1/transactions/:id` |
| `create_transaction` | `{ accountId: z.string().uuid(), amount: z.number().positive(), type: z.enum(["INCOME","EXPENSE","LEND","BORROW"]), transactionDate: z.string(), description: z.string().optional(), categoryId: z.string().uuid().optional(), labelIds: z.array(z.string().uuid()).optional() }` | `POST /api/v1/transactions` |
| `update_transaction` | Same as create + `{ id: z.string().uuid() }` | `PUT /api/v1/transactions/:id` |
| `delete_transaction` | `{ id: z.string().uuid() }` | `DELETE /api/v1/transactions/:id` |
| `get_expenditure_summary` | `{}` (none) | `GET /api/v1/transactions/expenditure-summary` |

#### `mcp-server/src/tools/transfers.ts` — 5 tools

| Tool Name | Input Schema | API Call |
|-----------|-------------|----------|
| `list_transfers` | `{ search: z.string().optional(), startDate: z.string().optional(), endDate: z.string().optional(), page: z.number().optional().default(0), size: z.number().optional().default(20) }` | `GET /api/v1/transfers` |
| `get_transfer` | `{ id: z.string().uuid() }` | `GET /api/v1/transfers/:id` |
| `create_transfer` | `{ fromAccountId: z.string().uuid(), toAccountId: z.string().uuid(), fromAmount: z.number().optional(), toAmount: z.number().optional(), adjustment: z.number().optional(), transactionDate: z.string(), description: z.string(), categoryId: z.string().uuid().optional(), labelIds: z.array(z.string().uuid()).optional() }` | `POST /api/v1/transfers` |
| `update_transfer` | Same as create + `{ id: z.string().uuid() }` | `PUT /api/v1/transfers/:id` |
| `delete_transfer` | `{ id: z.string().uuid() }` | `DELETE /api/v1/transfers/:id` |

> **Note for transfers**: Exactly 2 of `fromAmount`, `toAmount`, `adjustment` must be provided. Add a description to help the AI: `"Provide exactly 2 of fromAmount, toAmount, adjustment. The 3rd is auto-computed."`

#### `mcp-server/src/tools/activity.ts` — 1 tool

| Tool Name | Input Schema | API Call |
|-----------|-------------|----------|
| `get_activity_feed` | `{ search: z.string().optional(), type: z.enum(["INCOME","EXPENSE","LEND","BORROW","TRANSFER"]).optional(), accountId: z.string().uuid().optional(), startDate: z.string().optional(), endDate: z.string().optional(), page: z.number().optional().default(0), size: z.number().optional().default(20) }` | `GET /api/v1/activity` |

#### `mcp-server/src/tools/categories.ts` — 4 tools

| Tool Name | Input Schema | API Call |
|-----------|-------------|----------|
| `list_categories` | `{}` | `GET /api/v1/categories` |
| `create_category` | `{ name: z.string(), icon: z.string().optional() }` | `POST /api/v1/categories` |
| `update_category` | `{ id: z.string().uuid(), name: z.string(), icon: z.string() }` | `PUT /api/v1/categories/:id` |
| `delete_category` | `{ id: z.string().uuid() }` | `DELETE /api/v1/categories/:id` |

> **Note (icon required)**: `PUT` replaces the whole object — the backend sets `icon` unconditionally, so omitting it **erases the emoji**. Always fetch the category first (`list_categories`) and re-send its current icon when you don't intend to change it.
>
> **Note (deletion)**: **Any** category — including defaults (Food/Travel/Transfer) and in-use categories — can be deleted; there is no guard. The `category_id` FK is `ON DELETE SET NULL`, so transactions/transfers using it are **silently detached** (they just lose their category). Put this warning in the `delete_category` tool description so the AI confirms with the user first. Duplicate category names are rejected (400, case-insensitive).

#### `mcp-server/src/tools/labels.ts` — 1 tool (read-only)

| Tool Name | Input Schema | API Call |
|-----------|-------------|----------|
| `list_labels` | `{}` | `GET /api/v1/labels` |

---

### Step 9: MCP Server Setup

**File: `mcp-server/src/mcp/server.ts`**

```typescript
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { registerAccountTools } from '../tools/accounts.js';
import { registerTransactionTools } from '../tools/transactions.js';
// ... import others

export function createMcpServer(jwt: string, baseUrl: string): McpServer {
  const server = new McpServer({
    name: 'budget-tracker',
    version: '1.0.0',
  });

  registerAccountTools(server, jwt, baseUrl);
  registerTransactionTools(server, jwt, baseUrl);
  // ... register all tool groups

  return server;
}
```

---

### Step 10: Streamable HTTP Handler

**File: `mcp-server/src/mcp/handler.ts`**

This is the core MCP transport layer. You need an Express router that handles:

- `POST /mcp` — JSON-RPC requests from the MCP client
- `GET /mcp` — SSE stream for server-initiated notifications
- `DELETE /mcp` — Close a session

**Key concepts (verified against the 2025-03-26 spec and the current SDK):**
- The session ID travels in the **`Mcp-Session-Id` HTTP header** — never a query param. The server returns it on the `initialize` response; the client echoes it on **every** subsequent request (POST, GET, and DELETE).
- `StreamableHTTPServerTransport` is **stateful only if you pass `sessionIdGenerator`**. `new StreamableHTTPServerTransport()` with no options runs *stateless*: it never issues a session ID, and your session map would never fill up. Always pass `sessionIdGenerator: () => randomUUID()`.
- Current SDK 1.x exposes **one** entry method: `transport.handleRequest(req, res, parsedBody?)` for all three verbs. (`handlePostMessage`/`handleGetMessage`/`handleDeleteMessage` are from old 1.x point-releases and **no longer exist** — calling them throws `TypeError`.)
- You must pass **`req.body`** explicitly: `express.json()` in Step 11 consumes the request stream, so the transport cannot read the body itself.
- Populate the session map from the transport's **`onsessioninitialized`** callback (race-safe — requests can arrive before your code after `handleRequest` returns) and remove entries in **`transport.onclose`**.
- A non-`initialize` request with a missing/unknown session ID must get **400** per spec.
- **Auth applies to all three routes**, and every 401 must use the `send401` helper from Step 3 (`WWW-Authenticate` + resource-metadata pointer) — that header is what makes clients re-run the OAuth flow when the stored Budget Tracker JWT expires mid-session.

**Structure:**
```typescript
import { randomUUID } from 'node:crypto';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { isInitializeRequest } from '@modelcontextprotocol/sdk/types.js';
import { createMcpServer } from './server.js';
import { getJwtForToken } from '../oauth/store.js';
import { send401 } from '../oauth/metadata.js'; // from Step 3 — sets WWW-Authenticate, sends 401

const sessions = new Map<string, StreamableHTTPServerTransport>();

// Extract + validate the bearer token. Returns the Budget Tracker JWT, or null (after sending 401).
function requireBearer(req: express.Request, res: express.Response): string | null {
  const authHeader = req.headers.authorization;
  if (!authHeader?.startsWith('Bearer ')) {
    send401(res, 'Missing Bearer token');
    return null;
  }
  const jwt = getJwtForToken(authHeader.split(' ')[1]);
  // Eagerly reject if the stored Budget Tracker JWT is gone or expired:
  // a 401 *with the WWW-Authenticate header* makes the client re-trigger the OAuth flow.
  if (!jwt || isJwtExpired(jwt)) {
    send401(res, 'Unauthorized or token expired');
    return null;
  }
  return jwt;
}

// Helper to check if Budget Tracker JWT is expired
function isJwtExpired(token: string): boolean {
  try {
    const payloadBase64 = token.split('.')[1];
    const payload = JSON.parse(Buffer.from(payloadBase64, 'base64url').toString('utf8'));
    return payload.exp * 1000 < Date.now();
  } catch {
    return true; // invalid token
  }
}

// POST /mcp — every JSON-RPC message arrives here
router.post('/mcp', async (req, res) => {
  const jwt = requireBearer(req, res);
  if (!jwt) return;

  const sessionId = req.headers['mcp-session-id'] as string | undefined;
  let transport = sessionId ? sessions.get(sessionId) : undefined;

  if (!transport) {
    if (sessionId || !isInitializeRequest(req.body)) {
      // Unknown/absent session on a non-initialize request → 400 per spec
      res.status(400).json({
        jsonrpc: '2.0', error: { code: -32000, message: 'Bad Request: No valid session ID provided' }, id: null,
      });
      return;
    }
    // New session: stateful transport + one McpServer per session, closed over this user's JWT
    transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: () => randomUUID(),
      onsessioninitialized: (sid) => { sessions.set(sid, transport!); },
    });
    transport.onclose = () => {
      const sid = transport!.sessionId;
      if (sid) delete sessions[sid];
    };
    const baseUrl = process.env.BUDGET_TRACKER_URL || 'http://localhost:3300';
    const server = createMcpServer(jwt, baseUrl);
    await server.connect(transport); // connect BEFORE handling, so responses can flow back
  }

  await transport.handleRequest(req, res, req.body); // req.body: express.json() already consumed the stream
});

// GET /mcp — SSE stream for server-initiated messages (the transport speaks SSE itself)
router.get('/mcp', async (req, res) => {
  if (!requireBearer(req, res)) return;
  const transport = sessions.get(req.headers['mcp-session-id'] as string);
  if (!transport) return res.status(400).send('Invalid or missing session ID');
  await transport.handleRequest(req, res);
});

// DELETE /mcp — session termination (the transport deletes state and fires onclose)
router.delete('/mcp', async (req, res) => {
  if (!requireBearer(req, res)) return;
  const transport = sessions.get(req.headers['mcp-session-id'] as string);
  if (!transport) return res.status(400).send('Invalid or missing session ID');
  await transport.handleRequest(req, res);
});
```

> **Refer to the official SDK example**: `src/examples/server/simpleStreamableHttp.ts` in https://github.com/modelcontextprotocol/typescript-sdk (branch `v1.x`) — the structure above mirrors it, minus the SDK's built-in auth middleware (`requireBearerAuth` / `mcpAuthMetadataRouter`), which we replace with our token-store lookup because our OAuth server lives in this same process.

---

### Step 11: Express Entry Point

**File: `mcp-server/src/index.ts`**

```typescript
import express from 'express';
import { metadataRouter } from './oauth/metadata.js';
import { registerRouter } from './oauth/register.js';
import { authorizeRouter } from './oauth/authorize.js';
import { tokenRouter } from './oauth/token.js';
import { mcpRouter } from './mcp/handler.js';

const app = express();
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// DNS-rebinding protection (MUST per the MCP spec's security considerations).
// Native/CLI MCP clients send no Origin header at all; browsers hitting this
// server will always send a localhost origin (the OAuth login form included).
const ALLOWED_ORIGIN = /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/;
app.use((req, res, next) => {
  const origin = req.headers.origin;
  if (origin && !ALLOWED_ORIGIN.test(origin)) {
    return res.status(403).send('Forbidden: disallowed Origin');
  }
  next();
});

// OAuth endpoints
app.use(metadataRouter);
app.use(registerRouter);
app.use(authorizeRouter);
app.use(tokenRouter);

// MCP endpoint
app.use(mcpRouter);

const port = parseInt(process.env.MCP_PORT || '3001');
// Bind loopback by default when run directly (spec recommendation).
// IMPORTANT: in Docker set MCP_HOST=0.0.0.0 — inside a container, 127.0.0.1
// is unreachable through published ports. (Step 12's compose env does this.)
const host = process.env.MCP_HOST || '127.0.0.1';
app.listen(port, host, () => {
  console.log(`Budget Tracker MCP server running on http://localhost:${port}`);
  console.log(`MCP endpoint: http://localhost:${port}/mcp`);
  console.log(`Budget Tracker API: ${process.env.BUDGET_TRACKER_URL || 'http://localhost:3300'}`);
});
```

**Environment variables:**
| Variable | Default | Description |
|----------|---------|-------------|
| `MCP_PORT` | `3001` | Port for the MCP server |
| `MCP_HOST` | `127.0.0.1` | Bind address (set `0.0.0.0` in Docker, see Step 12) |
| `BUDGET_TRACKER_URL` | `http://localhost:3300` | Budget Tracker backend URL |
| `MCP_BASE_URL` | `http://localhost:3001` | Public URL of this MCP server (used in OAuth metadata + `WWW-Authenticate` 401 pointer) |

---

### Step 12: Docker & Makefile Integration

**`mcp-server/Dockerfile`**
```dockerfile
FROM node:22-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --production=false
COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build
RUN npm prune --production
CMD ["node", "dist/index.js"]
```

**Add to `docker-compose.yml`** (add as a 3rd service):
```yaml
  mcp:
    build: ./mcp-server
    container_name: budget-tracker-mcp
    ports:
      - "3001:3001"
    environment:
      BUDGET_TRACKER_URL: http://backend:8080
      MCP_BASE_URL: http://localhost:3001
      MCP_PORT: "3001"
      MCP_HOST: "0.0.0.0"   # required in Docker: published ports can't reach a 127.0.0.1 bind
    depends_on:
      backend:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://127.0.0.1:3001/.well-known/oauth-authorization-server > /dev/null || exit 1"]
      interval: 5s
      timeout: 3s
      retries: 12
    networks:
      - budget-tracker-net
```

> **Note on Makefile**: Because `run-stack` and `run-demo` use `docker compose up`, the new `mcp` service will be picked up automatically. The only Makefile change needed anywhere in this guide is the `test-mcp` target added in Step 14.

---

### Step 13: Unit & Component Tests

**Files: `mcp-server/src/**/*.test.ts`** (co-located with the code they test), run with **vitest** (already in your devDependencies): `cd mcp-server && npm test`.

This is the layer that guards the security requirements the guide keeps flagging (exact redirect_uri match, HTML escaping, PKCE, single-use codes, Origin checks, session handling). None of those paths are exercised by clicking through the browser flow — they must be asserted.

**Two small refactors first (they make the code testable):**

1. **Export the app without listening** — in `index.ts`, wrap the Express app in `export function createApp() { ... }` and only call `app.listen(...)` when run directly: `if (import.meta.url === \`file://${process.argv[1]}\`) { ... }`. Tests use `supertest(createApp())`.
2. **Inject the API client factory** — in `mcp/server.ts`: `export type MakeClient = (baseUrl: string, jwt: string) => BudgetTrackerClient;` and `createMcpServer(jwt, baseUrl, makeClient = (b, j) => new BudgetTrackerClient(b, j))`. Tests pass a fake factory that records which JWT each session closed over and returns canned data — no Budget Tracker backend needed.

**Test matrix** (write one `describe` block per row):

| # | Area | Case | Expect |
|---|------|------|--------|
| 1 | metadata | Both `/.well-known/...` endpoints | 200; every URL derived from `MCP_BASE_URL` |
| 2 | auth | `POST`/`GET`/`DELETE /mcp` with no/unknown bearer | 401 **with** `WWW-Authenticate: Bearer resource_metadata="..."` on **all three** routes |
| 3 | register | `POST /register` | `client_id` returned and `redirect_uris` stored |
| 4 | authorize | Unknown `client_id` (GET and POST) | 400; login API never called |
| 5 | authorize | `redirect_uri` not in the client's registered list | 400 (both verbs) — the open-redirect guard |
| 6 | authorize | Malicious `state`/`redirect_uri` like `"><script>alert(1)</script>` | Served HTML contains the escaped form (`&lt;script&gt;`) only |
| 7 | authorize | Valid credentials (fake login fetch) | 302 to `redirect_uri` carrying `code` **and** `state` |
| 8 | token | Wrong `code_verifier` | 400 `invalid_grant` |
| 9 | token | Correct verifier | access token issued |
| 10 | token | Same code exchanged **twice** | second attempt → `invalid_grant` (codes are single-use) |
| 11 | token | `redirect_uri`/`client_id` differ from the stored code | 400 `invalid_grant` |
| 12 | origin | `Origin: https://evil.com` | 403; missing and localhost origins pass |
| 13 | transport | `initialize` response carries `Mcp-Session-Id`; follow-up POST without the header / with unknown id | 400 on the follow-ups; unknown-session DELETE → 400 |
| 14 | tools | `tools/list` contains **exactly the 19 planned tools**, and none of `create_account`/`update_account`/`delete_account`/`create_label`/`update_label`/`delete_label` | the read-only contract, guarded against regressions |
| 15 | isolation | Two sessions, different bearer tokens (→ different mapped JWTs) | the fake client factory records **each session's own JWT** — no cross-user bleed. This catches any accidental module-level client/JWT sharing |
| 16 | units | `verifyPkce` (true/false), `escapeHtml` (all 5 characters) | — |

> **TTL testing tip**: read the auth-code TTL from an env like `AUTH_CODE_TTL_MS` (default 300000) and token TTL likewise, so tests can set them to ~50 ms and assert expiry without waiting minutes. Same for `isJwtExpired` — build JWTs with a past `exp`.

---

### Step 14: Demo-Data Smoke Test & `make test-mcp`

The repo's testing philosophy: `make test-int` and `make test-e2e` spin up the Dockerized stack and test against the *real* running app. Do the same for MCP: **`make test-mcp` brings up the demo stack (fresh DB → `DataSeeder` writes a known fixture), runs the Step 13 unit suite, then runs a smoke script that drives the MCP server exactly like an AI client would — full OAuth login, then tool calls — and asserts the tool calls return the data the demo seed actually wrote.**

**The assertions lean on the seeded fixture (`DataSeeder`), which is deterministic on a fresh volume:**

| Smoke step | Expected result |
|---|---|
| `tools/list` | Exactly 19 tools; no account/label write tools |
| `list_accounts` | Names exactly: `Main Bank`, `Cash`, `Visa Credit`, `Bob (Lend)` |
| `list_categories` | `Food` present with icon 🍔 |
| `list_labels` | Names exactly: `BILLS`, `FUN`, `NEEDS`, `SAVINGS`, `WANTS` |
| `get_expenditure_summary` | `today` ≥ 25 (seeded "Lunch" expense is dated now); `thisWeek` ≥ `today`; `thisMonth` ≥ `thisWeek` |
| `list_transactions` `search="Salary credit"` | ≥ 3 hits (the monthly salary recurrence exists across all 3 seeded cycles) |
| `get_activity_feed` `size=10` | Sorted newest-first; `Lunch` present |
| `list_transfers` | Contains `ATM Withdrawal` with `fromAmount` 50, `adjustment` 5, `toAmount` **55** — the seeded auto-computed third field |
| `create_transaction` (EXPENSE 10, now, Main Bank) → summary | `today` moves by **exactly +10** |
| `delete_transaction` → summary | `today` back to baseline (delete reverts totals) |
| `create_transfer` (`fromAmount` 40, `adjustment` −2) | `toAmount` auto-computed to **38** |
| `create_transfer` with from = to account | Result is a tool error (`isError`) — the API's "must be different" surfaces to the AI |
| `create_category` → `update_category` new icon → `delete_category` | Icon round-trips; category cleaned up afterwards |

Every mutation the smoke test performs is deleted again, so the demo fixture survives the run; creates and deletes apply/revert balances and totals symmetrically, so balances end where they started.

**File: `mcp-server/src/test/tool-smoke.ts`** — a plain script run with `tsx` (`npm run smoke`). OAuth over `fetch` (no browser needed), tool calls through the official SDK client:

```typescript
/**
 * Demo-data smoke: OAuth flow + tool-call assertions against seeded demo data.
 * Exit 0 = pass. Run via `npm run smoke` (Makefile: `make test-mcp`).
 */
import { randomBytes, createHash } from 'node:crypto';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';

const MCP = process.env.MCP_URL || 'http://localhost:3001';
const EMAIL = process.env.TEST_EMAIL || 'test@example.com';
const PASSWORD = process.env.TEST_PASSWORD || 'password';
const CB = 'http://localhost:1/cb';

let failures = 0;
const check = (name: string, ok: boolean, detail = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `  (${detail})` : ''}`);
  if (!ok) failures++;
};
const num = (v: unknown) => Number(v ?? 0);
const nearly = (a: number, b: number, eps = 0.001) => Math.abs(a - b) < eps;
const text = (res: any) => JSON.parse(res.content[0].text);

async function login(): Promise<string> {
  const reg: any = await fetch(`${MCP}/register`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ client_name: 'smoke', redirect_uris: [CB] }),
  }).then(r => r.json());

  const verifier = randomBytes(32).toString('base64url');
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  const auth = await fetch(`${MCP}/authorize`, {
    method: 'POST', redirect: 'manual',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: reg.client_id, redirect_uri: CB, code_challenge: challenge,
      code_challenge_method: 'S256', state: 'smoke', email: EMAIL, password: PASSWORD,
    }),
  });
  const code = new URL(auth.headers.get('location')!, CB).searchParams.get('code');

  const tok: any = await fetch(`${MCP}/token`, {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code', code: code!, redirect_uri: CB,
      client_id: reg.client_id, code_verifier: verifier,
    }),
  }).then(r => r.json());
  if (!tok.access_token) throw new Error(`OAuth failed: ${JSON.stringify(tok)}`);
  return tok.access_token;
}

async function main() {
  const client = new Client({ name: 'smoke', version: '1.0.0' });
  await client.connect(new StreamableHTTPClientTransport(new URL(`${MCP}/mcp`), {
    requestInit: { headers: { Authorization: `Bearer ${await login()}` } },
  }));
  const call = (name: string, args: any) => client.callTool({ name, arguments: args });

  // ── Read-only contract + seeded fixtures ────────────────────────────
  const tools = (await client.listTools()).tools.map(t => t.name);
  check('exactly 19 tools', tools.length === 19, String(tools.length));
  for (const gone of ['create_account', 'update_account', 'delete_account',
                      'create_label', 'update_label', 'delete_label'])
    check(`no ${gone} tool`, !tools.includes(gone));

  const accounts = text(await call('list_accounts', {}));
  check('demo accounts', accounts.map((a: any) => a.name).sort().join() ===
        ['Bob (Lend)', 'Cash', 'Main Bank', 'Visa Credit'].join());

  const labels = text(await call('list_labels', {}));
  check('demo labels', labels.map((l: any) => l.name).sort().join() ===
        'BILLS,FUN,NEEDS,SAVINGS,WANTS');

  const cats = text(await call('list_categories', {}));
  check('Food icon 🍔', cats.find((c: any) => c.name === 'Food')?.icon === '🍔');

  const summary = async () => text(await call('get_expenditure_summary', {}));
  const s0 = await summary();
  check('today ≥ seeded Lunch', num(s0.today) >= 25, `today=${s0.today}`);
  check('thisWeek ≥ today', num(s0.thisWeek) >= num(s0.today));
  check('thisMonth ≥ thisWeek', num(s0.thisMonth) >= num(s0.thisWeek));

  const txns = text(await call('list_transactions', { search: 'Salary credit', size: 50 }));
  check('salary credit ≥ 3', txns.totalElements >= 3, String(txns.totalElements));

  const feed = text(await call('get_activity_feed', { size: 10 }));
  check('Lunch in recent activity', feed.content.some((i: any) => i.description === 'Lunch'));

  const transfers = text(await call('list_transfers', { size: 50 }));
  const atm = transfers.content.find((t: any) => t.description === 'ATM Withdrawal');
  check('seeded transfer toAmount=55', !!atm && num(atm.fromAmount) === 50 && num(atm.toAmount) === 55);

  // ── Write path: summary must move by exactly +10 and come back ──────
  const mainBank = accounts.find((a: any) => a.name === 'Main Bank').id;
  const cash = accounts.find((a: any) => a.name === 'Cash').id;
  const base = num((await summary()).today);
  const created = text(await call('create_transaction', {
    accountId: mainBank, amount: 10, type: 'EXPENSE',
    transactionDate: new Date().toISOString(), description: 'MCP smoke tx',
  }));
  check('summary today +10', nearly(num((await summary()).today), base + 10), `base=${base}`);
  await call('delete_transaction', { id: created.id });
  check('summary back to baseline', nearly(num((await summary()).today), base));

  // ── Transfer auto-compute + error surface + cleanup ─────────────────
  const tr = text(await call('create_transfer', {
    fromAccountId: mainBank, toAccountId: cash, fromAmount: 40, adjustment: -2,
    transactionDate: new Date().toISOString(), description: 'MCP smoke transfer',
  }));
  check('toAmount auto-computed (40, −2) → 38', nearly(num(tr.toAmount), 38));
  await call('delete_transfer', { id: tr.id });

  const bad = await call('create_transfer', {
    fromAccountId: mainBank, toAccountId: mainBank, fromAmount: 5, toAmount: 5,
    transactionDate: new Date().toISOString(), description: 'should fail',
  });
  check('same-account transfer → isError', (bad as any).isError === true);

  const cat = text(await call('create_category', { name: 'ZZ MCP Smoke', icon: '🧪' }));
  const upd = text(await call('update_category', { id: cat.id, name: 'ZZ MCP Smoke', icon: '🧫' }));
  check('icon round-trips', upd.icon === '🧫');
  await call('delete_category', { id: cat.id });

  await client.close();
  console.log(failures ? `\n${failures} check(s) FAILED` : '\nAll checks passed');
  process.exit(failures ? 1 : 0);
}

main().catch(e => { console.error('Smoke run crashed:', e); process.exit(1); });
```

**File: `docker-compose.test-mcp.yml`** — its own **fresh** DB volume so `DataSeeder` re-seeds every run (same trick as `pgdata_test_e2e` in `docker-compose.e2e.yml`):

```yaml
services:
  postgres:
    volumes:
      - pgdata_test_mcp:/var/lib/postgresql

volumes:
  pgdata_test_mcp:
```

**Makefile target** (follows the `test-int`/`test-e2e` house style — add `test-mcp` to `.PHONY`; ⚠️ recipe lines must be indented with **tabs**, not spaces):

```make
# Unit tests + demo-data MCP tool-call smoke test against the demo stack
test-mcp: build
	@echo "Starting stack for MCP tests (postgres + seeded backend + mcp)..."
	@-docker volume rm budget_tracker_pgdata_test_mcp 2>/dev/null || true
	docker compose -f docker-compose.yml -f docker-compose.demo.yml -f docker-compose.test-mcp.yml up -d --build
	@echo "Waiting for backend to be healthy..."
	@n=0; until [ $$(docker inspect --format='{{.State.Health.Status}}' budget-tracker-backend) = 'healthy' ] || [ $$n -ge 30 ]; do sleep 2; n=$$(($$n + 1)); done; \
	if [ $$n -ge 30 ]; then \
	  echo "Error: Backend failed to become healthy"; \
	  docker compose -f docker-compose.yml -f docker-compose.demo.yml -f docker-compose.test-mcp.yml down; \
	  exit 1; \
	fi
	@echo "Waiting for MCP server to be healthy..."
	@n=0; until [ $$(docker inspect --format='{{.State.Health.Status}}' budget-tracker-mcp) = 'healthy' ] || [ $$n -ge 30 ]; do sleep 2; n=$$(($$n + 1)); done; \
	if [ $$n -ge 30 ]; then \
	  echo "Error: MCP server failed to become healthy"; \
	  docker compose -f docker-compose.yml -f docker-compose.demo.yml -f docker-compose.test-mcp.yml down; \
	  exit 1; \
	fi
	@EXIT_CODE=0; \
	echo "Running unit/component tests..."; \
	(cd mcp-server && npm ci && npm test) || EXIT_CODE=1; \
	echo "Running demo-data tool-call smoke test..."; \
	if [ $$EXIT_CODE -eq 0 ]; then (cd mcp-server && npm run smoke) || EXIT_CODE=1; fi; \
	echo "Tearing down MCP test stack..."; \
	docker compose -f docker-compose.yml -f docker-compose.demo.yml -f docker-compose.test-mcp.yml down; \
	docker volume rm budget_tracker_pgdata_test_mcp 2>/dev/null || true; \
	if [ $$EXIT_CODE -eq 0 ]; then echo "MCP tests completed successfully."; else echo "MCP tests failed."; fi; \
	exit $$EXIT_CODE
```

**Environment knobs** (all optional):

| Variable | Default | Used by |
|----------|---------|---------|
| `MCP_URL` | `http://localhost:3001` | smoke script (host → published `mcp` port) |
| `TEST_EMAIL` | `test@example.com` | smoke script (demo login) |
| `TEST_PASSWORD` | `password` | smoke script |

**Why this mirrors the repo's existing layers:**

- `npm test` ≈ the backend's unit tests — fast, no Docker, fakes at the REST boundary.
- `make test-mcp` ≈ `test-int`/`test-e2e` — real Docker stack, real OAuth flow, real Spring backend, real Postgres; a green run means an AI client can actually log in and get truthful numbers back.
- The smoke test is also the cheapest full-chain regression for the OAuth wrapper, the session/JWT plumbing (Step 10's per-session closure), and the read-only tool contract — all through one command.

**Caveats (put them in the Makefile comment):**
- The smoke test's `+10` delta check assumes the run doesn't cross midnight in `Asia/Kolkata` (the app's zone) between reading the baseline and re-checking — if the stack is left up across midnight the `today ≥ 25` baseline check will fail loudly, never silently.
- The stack can't run alongside `run-demo`/`test-e2e` (fixed `container_name`s) — same constraint as the existing test targets.
- Requires a committed `package-lock.json` (`npm ci` is used by both the Dockerfile and the target) — run `npm install` once and commit it.

---

## 6. Task Checklist

Check off each task as you complete it. Do them in order — later steps depend on earlier ones.

### Phase 1: Scaffolding
- [ ] Create `mcp-server/` directory
- [ ] Create `package.json` (copy from Step 1)
- [ ] Create `tsconfig.json` (copy from Step 1)
- [ ] Create `.gitignore`
- [ ] Run `npm install` — verify it succeeds
- [ ] Run `npm run build` — verify empty build succeeds (create an empty `src/index.ts` with a console.log)

### Phase 2: OAuth Layer
- [ ] `src/oauth/store.ts` — Implement all stores and helper functions
- [ ] `src/oauth/metadata.ts` — Implement BOTH metadata endpoints (`/.well-known/oauth-authorization-server` **and** `/.well-known/oauth-protected-resource`) + the `send401` helper, test with `curl http://localhost:3001/.well-known/oauth-authorization-server` and `curl http://localhost:3001/.well-known/oauth-protected-resource`
- [ ] `src/oauth/register.ts` — Implement registration, test with `curl -X POST http://localhost:3001/register -H 'Content-Type: application/json' -d '{"client_name":"test","redirect_uris":["http://localhost:9999/cb"]}'`
- [ ] `src/oauth/authorize.ts` — Implement GET (serve embedded login HTML **with `escapeHtml` on every interpolated value**) and POST; validate `client_id` + **exact-match `redirect_uri`** on BOTH routes
- [ ] `src/oauth/token.ts` — Implement token exchange with PKCE verification + `redirect_uri`/`client_id` match against the stored code

### Phase 3: API Client
- [ ] `src/api/client.ts` — Implement all methods
- [ ] Manually test a few methods against a running Budget Tracker (`make run-demo`)

### Phase 4: MCP Tools
- [ ] `src/tools/accounts.ts` — 2 tools (read-only)
- [ ] `src/tools/transactions.ts` — 6 tools (including expenditure summary)
- [ ] `src/tools/transfers.ts` — 5 tools
- [ ] `src/tools/activity.ts` — 1 tool
- [ ] `src/tools/categories.ts` — 4 tools
- [ ] `src/tools/labels.ts` — 1 tool (read-only)

### Phase 5: MCP Transport
- [ ] `src/mcp/server.ts` — Create McpServer per session (closed over the user's JWT), register all tools with `registerTool`
- [ ] `src/mcp/handler.ts` — Streamable HTTP handler: sessions keyed by the **`Mcp-Session-Id` header**, `sessionIdGenerator: () => randomUUID()`, map filled via `onsessioninitialized` / cleared via `onclose`, single `handleRequest(req, res, req.body)` entry point, `send401` (with `WWW-Authenticate`) on all three routes
- [ ] `src/index.ts` — Wire everything in Express: JSON + urlencoded parsers, **Origin allow-list middleware**, listen on `MCP_HOST` (default `127.0.0.1`)

### Phase 6: Build & Test
- [ ] `npm run build` — compiles without errors
- [ ] Start Budget Tracker: `make run-demo`
- [ ] Start MCP server: `cd mcp-server && npm start`
- [ ] `curl -i -X POST http://localhost:3001/mcp` (no token) → expect **401 with a `WWW-Authenticate: Bearer resource_metadata="..."` header**
- [ ] Test with MCP Inspector: `npx @modelcontextprotocol/inspector http://localhost:3001/mcp`
- [ ] Verify OAuth flow works (browser opens, login succeeds, tools appear)
- [ ] Verify a session is established: after initialize, subsequent requests carry the `Mcp-Session-Id` header (visible in Inspector's request log); the DELETE terminates it
- [ ] Test at least one tool from each category
- [ ] Expired-token recovery: restart the MCP server (empty token store) while the Inspector session is open → next request gets 401 and the Inspector re-runs the browser login
- [ ] Negative paths (covered by the Step 13 suite, `npm test`): redirect_uri mismatch (GET **and** POST), PKCE failure, code reuse, Origin 403, session-ID 400s, 401-with-`WWW-Authenticate` on **all three** `/mcp` routes
- [ ] Two-session isolation test: different bearer tokens → each session's tools hold their **own** user's JWT/data

### Phase 7: Docker
- [ ] Create `mcp-server/Dockerfile`
- [ ] Add `mcp` service to `docker-compose.yml`
- [ ] Test `make run-stack` — all 3 services start
- [ ] Verify MCP server is accessible at `http://localhost:3001/mcp`

### Phase 8: Automated Tests (Steps 13–14)
- [ ] Refactors done: `createApp()` exported (listen guarded), client factory injectable in `mcp/server.ts`
- [ ] Step 13 test matrix green: `cd mcp-server && npm test` (all 16 rows, incl. the 19-tools read-only contract and two-session isolation)
- [ ] `docker-compose.test-mcp.yml` created; `test-mcp` target added to `Makefile` (tab-indented) and `.PHONY`
- [ ] `package-lock.json` committed
- [ ] `make test-mcp` green end-to-end: spins up demo stack, runs unit tests + demo-data tool-call smoke, tears everything down, propagates exit code
- [ ] Smoke-run log shows every check `PASS` (fixtures + exact +10 summary delta + auto-computed 38 + isError case)

---

## 7. Testing Guide

### Quick Smoke Test (no MCP client needed)

```bash
# 1. Start Budget Tracker
make run-demo

# 2. Start MCP server
cd mcp-server && npm start

# 3. Test OAuth metadata + protected resource metadata
curl http://localhost:3001/.well-known/oauth-authorization-server | jq .
curl http://localhost:3001/.well-known/oauth-protected-resource | jq .

# 3b. Unauthenticated /mcp must 401 AND advertise where auth lives (spec MUST)
curl -s -o /dev/null -D - -X POST http://localhost:3001/mcp | grep -i www-authenticate
# expect: WWW-Authenticate: Bearer resource_metadata="http://localhost:3001/.well-known/oauth-protected-resource"

# 4. Test dynamic registration
curl -X POST http://localhost:3001/register \
  -H 'Content-Type: application/json' \
  -d '{"client_name":"test","redirect_uris":["http://localhost:9999/cb"]}' | jq .

# 5. Open login page in browser
# Visit: http://localhost:3001/authorize?response_type=code&client_id=<from-step-4>&redirect_uri=http://localhost:9999/cb&code_challenge=test&code_challenge_method=S256&state=abc

# 6. Enter test@example.com / password → should redirect (will fail since localhost:9999 isn't running, but you'll see the code in the URL)
```

### Automated: `npm test` and `make test-mcp`

```bash
cd mcp-server && npm test     # Step 13: unit/component suite (no Docker, mocked REST client)

make test-mcp                 # Step 14: one command —
                              #   1. builds the backend jar
                              #   2. starts postgres + seeded backend + mcp on a FRESH demo volume
                              #   3. waits for both healthchecks
                              #   4. npm ci + npm test (unit suite)
                              #   5. npm run smoke (full OAuth login → tool calls asserted
                              #      against known DataSeeder fixtures)
                              #   6. tears the stack down and removes the volume, exit code propagates
```

`make test-mcp` is the acceptance test for the whole plan: if it passes, an AI client can really
run the OAuth dance and get truthful numbers out of your data. Add `-f` overrides
(`MCP_URL`, `TEST_EMAIL`, `TEST_PASSWORD`) only when your ports/credentials differ.

### MCP Inspector (recommended)

The MCP Inspector is an official tool for testing MCP servers:

```bash
npx @modelcontextprotocol/inspector http://localhost:3001/mcp
```

This opens a web UI where you can:
- See all discovered tools
- Call tools interactively
- View request/response details

### Common Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `401 Unauthorized` and client shows "auth failed"/never opens a browser | 401 is missing the `WWW-Authenticate: Bearer resource_metadata="..."` header — clients discover OAuth **through that header**; a bare 401 is a dead end | Use `send401` everywhere; verify with `curl -i -X POST .../mcp` (smoke test 3b) |
| `401 Unauthorized` mid-session | Stored Budget Tracker JWT expired (24h TTL) | Expected — the 401 + `WWW-Authenticate` makes the client re-run the browser login automatically |
| `TypeError: transport.handlePostMessage is not a function` | Old 1.x transport API — current 1.x only has `handleRequest` | Use `transport.handleRequest(req, res, req.body)` (Step 10) |
| Every request after `initialize` fails with 400 "No valid session ID" | Transport built without `sessionIdGenerator` (stateless → no session ID ever handed out), or session read from query param instead of the `Mcp-Session-Id` header | Pass `sessionIdGenerator: () => randomUUID()`; key the map from `onsessioninitialized`; read the **header** |
| POST /mcp hangs / body is undefined | `express.json()` consumed the stream and `req.body` wasn't passed to the transport | `handleRequest(req, res, req.body)` |
| `ECONNREFUSED` on port 3300 | Budget Tracker not running | Run `make run-demo` first |
| `invalid_grant` on token exchange | PKCE verification failed, code expired, or `redirect_uri`/`client_id` don't match the stored code | Check SHA256 + base64url encoding; compare the exact values used at `/authorize` |
| Tools not appearing | McpServer not connected to transport | Check that `server.connect(transport)` is called before `handleRequest` |
| MCP server unreachable in Docker (curl from host hangs/refused) | App bound to `127.0.0.1` *inside the container* | Set `MCP_HOST=0.0.0.0` in the compose service (Step 12) |
| `Cannot find module` | TypeScript not compiled | Run `npm run build` before `npm start` |
