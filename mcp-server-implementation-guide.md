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
   - [Step 13: Antigravity MCP Config](#step-13-antigravity-mcp-config)
6. [Task Checklist](#6-task-checklist)
7. [Testing Guide](#7-testing-guide)

---

## 1. What You're Building

An **MCP server** that lets AI assistants (Claude, Antigravity, Cursor, etc.) interact with the Budget Tracker app. Think of it as an API adapter: the AI calls MCP tools like `create_transaction` or `get_expenditure_summary`, and the MCP server translates those into REST API calls to the Budget Tracker backend.

**Key features:**
- **Streamable HTTP transport**: The server is a remote HTTP service. Users configure it by entering a URL (e.g., `http://localhost:3001/mcp`) — that's it.
- **OAuth 2.1 authentication with PKCE**: When an AI client connects for the first time, a browser opens with a login form. The user enters their Budget Tracker email + password. After that, everything is automatic.
- **22 tools** covering accounts, transactions, transfers, activity feed, expenditure summary, categories, and labels.

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
│  MCP Client (AI IDE like Antigravity/Claude Desktop/Cursor) │
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

1. MCP client connects → server says "you need auth" (401)
2. MCP client discovers OAuth endpoints via `/.well-known/oauth-authorization-server`
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
    │   ├── metadata.ts           # GET /.well-known/oauth-authorization-server
    │   ├── register.ts           # POST /register
    │   ├── authorize.ts          # GET+POST /authorize
    │   ├── token.ts              # POST /token
    │   └── login.html            # Login form page
    ├── api/
    │   └── client.ts             # Budget Tracker REST API client
    ├── mcp/
    │   ├── server.ts             # McpServer + tool registration
    │   └── handler.ts            # Streamable HTTP transport handler
    └── tools/
        ├── accounts.ts
        ├── transactions.ts
        ├── transfers.ts
        ├── activity.ts
        ├── categories.ts
        └── labels.ts
```

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
DELETE /api/v1/categories/:id        → 204
```

### 4.8 Labels

```
GET    /api/v1/labels                → Label[]
POST   /api/v1/labels                → Label     (body: { "name": "NEEDS" })
DELETE /api/v1/labels/:id            → 204
```

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
    "dev": "tsc --watch & node --watch dist/index.js"
  },
  "dependencies": {
    "@modelcontextprotocol/sdk": "^1.12.0",
    "express": "^5.1.0",
    "uuid": "^11.1.0",
    "zod": "^3.25.0"
  },
  "devDependencies": {
    "@types/express": "^5.0.0",
    "@types/node": "^22.0.0",
    "@types/uuid": "^10.0.0",
    "typescript": "^5.8.0"
  }
}
```

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
  "client_name": "Antigravity",
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
  "client_name": "Antigravity",
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
1. Validate that `client_id` exists in your registered clients.
2. Serve an HTML login form. The form must include hidden fields for all the OAuth params (client_id, redirect_uri, code_challenge, code_challenge_method, state).
3. The form has email + password fields and a Submit button.
4. The form POSTs to `/authorize`.

#### `POST /authorize` — Validate credentials and redirect

**What to do:**
1. Extract email, password, and all hidden OAuth fields from the form body.
2. Call Budget Tracker's login API:
   ```
   POST http://localhost:3300/api/v1/auth/login
   Content-Type: application/json
   Body: { "email": "...", "password": "..." }
   ```
3. If login fails → re-render login page with an error message.
4. If login succeeds → you get a JWT token from the response.
5. Generate a random authorization code (use `uuid`).
6. Store it: `storeAuthCode(code, { budgetTrackerJwt, codeChallenge, redirectUri, clientId })`.
7. Redirect the browser to: `{redirect_uri}?code={code}&state={state}`.

**File: `mcp-server/src/oauth/login.html`**

A simple, clean HTML login page. Must contain:
- Email input field
- Password input field
- Submit button
- Hidden inputs for: `client_id`, `redirect_uri`, `code_challenge`, `code_challenge_method`, `state`
- Error display area (for invalid credentials)
- Form action: `POST /authorize`

Style it simply (inline CSS is fine). Budget Tracker branding (title: "Budget Tracker — Sign In") is a nice touch.

> **How to serve the HTML**: Use `fs.readFileSync` to load the HTML template, then replace `{{placeholder}}` values with the actual OAuth params before sending it.

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
1. Look up the authorization code in your store via `consumeAuthCode(code)`.
2. If not found or expired → return `400 { "error": "invalid_grant" }`.
3. Verify PKCE: compute `SHA256(code_verifier)` → base64url-encode it → compare with stored `code_challenge`. They must match.
4. If PKCE fails → return `400 { "error": "invalid_grant" }`.
5. Generate a random access token (use `uuid`).
6. Store the mapping: `storeToken(accessToken, budgetTrackerJwt)`.
7. Return:
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
  async createAccount(data: any) { return this.request('POST', '/api/v1/accounts', data); }
  async updateAccount(id: string, data: any) { return this.request('PUT', `/api/v1/accounts/${id}`, data); }
  async deleteAccount(id: string) { return this.request('DELETE', `/api/v1/accounts/${id}`); }

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
  async deleteCategory(id: string) { return this.request('DELETE', `/api/v1/categories/${id}`); }

  // Labels
  async listLabels() { return this.request('GET', '/api/v1/labels'); }
  async createLabel(data: any) { return this.request('POST', '/api/v1/labels', data); }
  async deleteLabel(id: string) { return this.request('DELETE', `/api/v1/labels/${id}`); }
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
  getClient: (token: string) => BudgetTrackerClient
) {
  server.tool(
    'list_accounts',
    'List all accounts with their balances',
    {},  // no input params
    async (_params, extra) => {
      const jwt = extra.authInfo?.token as string;  // the Budget Tracker JWT
      const client = getClient(jwt);
      const accounts = await client.listAccounts();
      return { content: [{ type: 'text', text: JSON.stringify(accounts, null, 2) }] };
    }
  );

  // ... more tools
}
```

> **Important**: `extra.authInfo?.token` gives you the raw OAuth access token. You need to look up the corresponding Budget Tracker JWT from your token store.

Here are all the tools to implement per file:

#### `mcp-server/src/tools/accounts.ts` — 5 tools

| Tool Name | Input Schema (Zod) | API Call |
|-----------|-------------------|----------|
| `list_accounts` | `{}` (none) | `GET /api/v1/accounts` |
| `get_account` | `{ id: z.string().uuid() }` | `GET /api/v1/accounts/:id` |
| `create_account` | `{ name: z.string(), type: z.enum(["BANK","CREDIT_CARD","CASH","FRIEND_LENDING"]), initialBalance: z.number().optional().default(0), creditLimit: z.number().optional() }` | `POST /api/v1/accounts` |
| `update_account` | `{ id: z.string().uuid(), name: z.string().optional(), type: z.enum([...]).optional(), initialBalance: z.number().optional(), creditLimit: z.number().optional() }` | `PUT /api/v1/accounts/:id` |
| `delete_account` | `{ id: z.string().uuid() }` | `DELETE /api/v1/accounts/:id` |

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

#### `mcp-server/src/tools/categories.ts` — 3 tools

| Tool Name | Input Schema | API Call |
|-----------|-------------|----------|
| `list_categories` | `{}` | `GET /api/v1/categories` |
| `create_category` | `{ name: z.string(), icon: z.string().optional() }` | `POST /api/v1/categories` |
| `delete_category` | `{ id: z.string().uuid() }` | `DELETE /api/v1/categories/:id` |

#### `mcp-server/src/tools/labels.ts` — 2 tools

| Tool Name | Input Schema | API Call |
|-----------|-------------|----------|
| `list_labels` | `{}` | `GET /api/v1/labels` |
| `create_label` | `{ name: z.string() }` | `POST /api/v1/labels` |

---

### Step 9: MCP Server Setup

**File: `mcp-server/src/mcp/server.ts`**

```typescript
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { registerAccountTools } from '../tools/accounts.js';
import { registerTransactionTools } from '../tools/transactions.js';
// ... import others

export function createMcpServer(
  getClient: (jwt: string) => BudgetTrackerClient
): McpServer {
  const server = new McpServer({
    name: 'budget-tracker',
    version: '1.0.0',
  });

  registerAccountTools(server, getClient);
  registerTransactionTools(server, getClient);
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

**Key concepts:**
- Each client session has a unique `mcp-session-id` header.
- You maintain a `Map<string, StreamableHTTPServerTransport>` to route requests to the right transport.
- On the first POST (no session ID), create a new `StreamableHTTPServerTransport` and `McpServer`, connect them.

**Rough structure:**
```typescript
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { createMcpServer } from './server.js';

const sessions = new Map<string, StreamableHTTPServerTransport>();

// POST /mcp
router.post('/mcp', async (req, res) => {
  // 1. Check Authorization: Bearer <token> header
  //    → look up JWT from token store
  //    → if invalid: return 401
  // 2. Check mcp-session-id header
  //    → if exists and known: route to existing transport
  //    → if not: create new transport + server, connect them, store in sessions map
  // 3. Let the transport handle the request
});

// GET /mcp — SSE stream
router.get('/mcp', async (req, res) => {
  // Route to existing session's transport for SSE
});

// DELETE /mcp — close session
router.delete('/mcp', async (req, res) => {
  // Clean up session
});
```

> **Refer to the official SDK examples**: https://github.com/modelcontextprotocol/typescript-sdk — look at the `src/examples/` directory for `StreamableHTTPServerTransport` usage.

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

// OAuth endpoints
app.use(metadataRouter);
app.use(registerRouter);
app.use(authorizeRouter);
app.use(tokenRouter);

// MCP endpoint
app.use(mcpRouter);

const port = parseInt(process.env.MCP_PORT || '3001');
app.listen(port, () => {
  console.log(`Budget Tracker MCP server running on http://localhost:${port}`);
  console.log(`MCP endpoint: http://localhost:${port}/mcp`);
  console.log(`Budget Tracker API: ${process.env.BUDGET_TRACKER_URL || 'http://localhost:3300'}`);
});
```

**Environment variables:**
| Variable | Default | Description |
|----------|---------|-------------|
| `MCP_PORT` | `3001` | Port for the MCP server |
| `BUDGET_TRACKER_URL` | `http://localhost:3300` | Budget Tracker backend URL |
| `MCP_BASE_URL` | `http://localhost:3001` | Public URL of this MCP server (used in OAuth metadata) |

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
    depends_on:
      backend:
        condition: service_healthy
    networks:
      - budget-tracker-net
```

**Update `Makefile`** targets that should include the MCP server:
- `run-stack` and `run-demo`: Include `mcp` in the `docker compose up` command
- `stop-stack` and `stop-demo`: Include `mcp` in the `docker compose down` command
- Do NOT add `mcp` to test targets (`test-int`, `test-e2e`) — tests don't need the MCP server

---

### Step 13: Antigravity MCP Config

**File: `.agents/mcp_config.json`** (at the project root, NOT inside `mcp-server/`)

```json
{
  "mcpServers": {
    "budget-tracker": {
      "serverUrl": "http://localhost:3001/mcp"
    }
  }
}
```

This tells Antigravity (or any MCP client) where to find the MCP server. Everything else (OAuth discovery, browser login, token management) happens automatically.

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
- [ ] `src/oauth/metadata.ts` — Implement metadata endpoint, test with `curl http://localhost:3001/.well-known/oauth-authorization-server`
- [ ] `src/oauth/register.ts` — Implement registration, test with `curl -X POST http://localhost:3001/register -H 'Content-Type: application/json' -d '{"client_name":"test","redirect_uris":["http://localhost:9999/cb"]}'`
- [ ] `src/oauth/login.html` — Create login page HTML
- [ ] `src/oauth/authorize.ts` — Implement GET (serve login page) and POST (validate + redirect)
- [ ] `src/oauth/token.ts` — Implement token exchange with PKCE verification

### Phase 3: API Client
- [ ] `src/api/client.ts` — Implement all methods
- [ ] Manually test a few methods against a running Budget Tracker (`make run-demo`)

### Phase 4: MCP Tools
- [ ] `src/tools/accounts.ts` — 5 tools
- [ ] `src/tools/transactions.ts` — 6 tools (including expenditure summary)
- [ ] `src/tools/transfers.ts` — 5 tools
- [ ] `src/tools/activity.ts` — 1 tool
- [ ] `src/tools/categories.ts` — 3 tools
- [ ] `src/tools/labels.ts` — 2 tools

### Phase 5: MCP Transport
- [ ] `src/mcp/server.ts` — Create McpServer, register all tools
- [ ] `src/mcp/handler.ts` — Streamable HTTP handler with session management
- [ ] `src/index.ts` — Wire everything together in Express

### Phase 6: Build & Test
- [ ] `npm run build` — compiles without errors
- [ ] Start Budget Tracker: `make run-demo`
- [ ] Start MCP server: `cd mcp-server && npm start`
- [ ] Test with MCP Inspector: `npx @modelcontextprotocol/inspector http://localhost:3001/mcp`
- [ ] Verify OAuth flow works (browser opens, login succeeds, tools appear)
- [ ] Test at least one tool from each category

### Phase 7: Docker & Makefile
- [ ] Create `mcp-server/Dockerfile`
- [ ] Add `mcp` service to `docker-compose.yml`
- [ ] Update `Makefile` targets (run-stack, stop-stack, run-demo, stop-demo)
- [ ] Test `make run-stack` — all 3 services start
- [ ] Verify MCP server is accessible at `http://localhost:3001/mcp`

### Phase 8: Antigravity Config
- [ ] Create `.agents/mcp_config.json`
- [ ] Test in Antigravity: tools should appear after authenticating

---

## 7. Testing Guide

### Quick Smoke Test (no MCP client needed)

```bash
# 1. Start Budget Tracker
make run-demo

# 2. Start MCP server
cd mcp-server && npm start

# 3. Test OAuth metadata
curl http://localhost:3001/.well-known/oauth-authorization-server | jq .

# 4. Test dynamic registration
curl -X POST http://localhost:3001/register \
  -H 'Content-Type: application/json' \
  -d '{"client_name":"test","redirect_uris":["http://localhost:9999/cb"]}' | jq .

# 5. Open login page in browser
# Visit: http://localhost:3001/authorize?response_type=code&client_id=<from-step-4>&redirect_uri=http://localhost:9999/cb&code_challenge=test&code_challenge_method=S256&state=abc

# 6. Enter test@example.com / password → should redirect (will fail since localhost:9999 isn't running, but you'll see the code in the URL)
```

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
| `401 Unauthorized` from Budget Tracker | JWT expired or invalid | Re-authenticate (OAuth flow will trigger automatically) |
| `ECONNREFUSED` on port 3300 | Budget Tracker not running | Run `make run-demo` first |
| `invalid_grant` on token exchange | PKCE verification failed or code expired | Check your SHA256 + base64url encoding logic |
| Tools not appearing | McpServer not connected to transport | Check that `server.connect(transport)` is called |
| `Cannot find module` | TypeScript not compiled | Run `npm run build` before `npm start` |
