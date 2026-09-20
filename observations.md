# Observations — MCP Server Implementation Review

> Code review of the `mcp-server/` implementation, cross-checked against
> `mcp-server-implementation-guide.md`. Branch: `mcp-server`.
> Verified with `npm test` (22/22 pass) and `npm run build` (compiles). All
> `api/client.ts` paths confirmed against the backend controllers.

## Verdict
Functionally complete and security-sound. OAuth 2.1/PKCE, the 19-tool read-only
contract, and per-session isolation are implemented correctly. The gaps are in
**test coverage vs. the guide's own Phase 8 plan** and a couple of packaging
details — not in the runtime code.

## What is solid (verified)
- **Exactly 19 tools**, accounts + labels strictly read-only. `api/client.ts`
  exposes no account/label write methods; `tools/accounts.ts` and
  `tools/labels.ts` register only list/get.
- **Per-session isolation**: each `register*Tools` builds
  `new BudgetTrackerClient(baseUrl, jwt)` *inside* the function (e.g.
  `tools/transactions.ts:39`); `mcp/handler.ts:65` calls `createMcpServer(jwt,…)`
  per session. No module-level client/JWT.
- **OAuth correctness**:
  - Single-use codes — `oauth/store.ts:43` deletes before the expiry check.
  - Code bound to `redirect_uri` + `client_id` + challenge, re-checked at
    `oauth/token.ts:33-40`.
  - PKCE forced to S256 (`oauth/token.ts:5-8`); fails closed on `plain`.
  - Login form HTML-escaped (`oauth/authorize.ts:4-11`).
  - `WWW-Authenticate` set on 401 (`oauth/metadata.ts:7`).
  - Origin guard rejects non-localhost (`index.ts:13-21`).
  - Token expiry re-checked on every request (`mcp/handler.ts:11-34`).

## Issues

### High
1. **Step 14 (`make test-mcp`) not implemented** — even though the guide (added
   in commit `7300338`) describes it as the acceptance test. Missing:
   - `test-mcp` target in the Makefile (only `run-demo-mcp` exists),
   - `docker-compose.test-mcp.yml`,
   - `mcp-server/src/test/tool-smoke.ts`,
   - the `tsx` devDependency and `smoke` script in `mcp-server/package.json`.
   Net effect: there is **no end-to-end "an AI client can log in and get
   truthful numbers" test**.

### Medium
2. **`mcp-server/tsconfig.json` has no `exclude`.** `npm run build` compiles the
   5 `*.test.ts` files into `dist/` (confirmed: `dist/oauth/oauth.test.js`, etc.),
   and the `mcp-server/Dockerfile` runs `npm run build`, so test files ship in the
   image and the prod build is coupled to devDependency types. Add the guide's
   `"exclude": ["src/**/*.test.ts", "src/test/**"]`.
3. **The read-only contract is only half-guarded.** `mcp/session.test.ts` checks
   that the 19 tool names are present, but not that there are *exactly* 19, and
   never asserts the absence of
   `create_account`/`update_account`/`delete_account`/`create_label`/`update_label`/`delete_label`.
   A regression re-adding a write tool would still pass. Assert
   `tools.length === 19` and none-of-the-forbidden (guide Step 13, row 14).
4. **Tools have zero tests.** The guide's Step 13 testability refactor #2
   (injectable client factory in `createMcpServer`) was not done —
   `mcp/server.ts:9` still takes a bare `jwt` — so tool arg-mapping (`paramsOf`),
   PUT semantics, and the two-session JWT-isolation assertion (row 15) can't be
   tested without a live backend.

### Low / to-verify
5. **`z.string().uuid()` vs UUIDv7** (`tools/*.ts`): confirm zod `^3.25` accepts
   the app's UUIDv7 IDs; a too-strict regex would silently break `get/update/delete`
   by ID. Currently untested.
6. **Sessions bound only by `sessionId`**, not the authenticating principal
   (`mcp/handler.ts:42-45,74,84`): any valid bearer + a guessed session id could
   drive another user's transport. Low risk (ids are `randomUUID`) but there is no
   owner binding.
7. **`loginToBudgetTracker` couples to `data.token ?? data.accessToken`**
   (`oauth/authorize.ts:64`) — depends on the backend's exact login body; worth a
   test asserting the real shape.
8. **`oauth/store.ts` TTLs are hardcoded** (5 min / 24 h); tests work around it by
   mutating `expiresAt`. The guide suggested env-configurable TTLs for faster
   expiry tests. Optional.

## Suggested order
1. `tsconfig` `exclude` + strengthen the 19-tool assertion (quick wins).
2. Step 14 with the injectable-client refactor first, then the smoke test +
   `make test-mcp` + `docker-compose.test-mcp.yml`.
