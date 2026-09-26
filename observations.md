# Observations — MCP Server Implementation Review

> Code review of the `mcp-server/` implementation, cross-checked against
> `mcp-server-implementation-guide.md`. Branch: `mcp-server`.
> Verified with `npm test` (`e29e559` expanded the suite) and `npm run build`
> (compiles). All `api/client.ts` paths confirmed against the backend controllers.
>
> **Status: refreshed 2026-09-23** — the original review's High/Medium items have
> since been resolved by `7300338`/`30275af`/`09e8220`/`55a5c42`/`e29e559`.

## Verdict
Functionally complete and security-sound. OAuth 2.1/PKCE, the 19-tool read-only
contract, and per-session isolation (including session↔principal binding) are
implemented correctly **and now verified by tests**. The remaining gaps are
low-risk (an untested UUIDv7 edge case, hardcoded OAuth TTLs) plus doc hygiene —
not in the runtime code.

## What is solid (verified)
- **Exactly 19 tools**, accounts + labels strictly read-only. `api/client.ts`
  exposes no account/label write methods; `tools/accounts.ts` and
  `tools/labels.ts` register only list/get. Enforced by tests:
  - `tools.test.ts:29-52` asserts the exact 19-tool set (`length === 19`) and
    the absence of all six write tools.
  - `session.test.ts` re-checks presence/gone across the HTTP layer.
- **Per-session isolation**: each `register*Tools` builds
  `new BudgetTrackerClient(baseUrl, jwt)` *inside* the function (e.g.
  `tools/transactions.ts:39`); `mcp/handler.ts:65` calls `createMcpServer(jwt,…)`
  per session. No module-level client/JWT. Now testably enforced via an
  injectable `makeClient` (`server.ts:9`, `handler.ts` default param) —
  `tools.test.ts` asserts every session's 6 tool clients are bound to its own JWT
  with no cross-user bleed.
- **OAuth correctness**:
  - Single-use codes — `oauth/store.ts:43` deletes before the expiry check.
  - Code bound to `redirect_uri` + `client_id` + challenge, re-checked at
    `oauth/token.ts:33-40`.
  - PKCE forced to S256 (`oauth/token.ts:5-8`); fails closed on `plain`.
  - Login form HTML-escaped (`oauth/authorize.ts:4-11`).
  - `WWW-Authenticate` set on 401 (`oauth/metadata.ts:7`).
  - Origin guard rejects non-localhost (`index.ts:13-21`).
  - Token expiry re-checked on every request (`mcp/handler.ts:11-34`).

## Previously filed issues — status

### High
1. **Step 14 (`make test-mcp`) not implemented** — ✅ **RESOLVED** (`e29e559`).
   `test-mcp` target now exists in the Makefile and orchestrates
   `docker-compose.test-mcp.yml` (demo stack), runs `npm test`, then the
   `npm run smoke` demo-data tool-call test against a seeded backend. There is
   now a genuine end-to-end "an AI client can log in and get truthful numbers"
   test (`tool-smoke.ts`, including the ±10 summary write-check).

### Medium
2. **`mcp-server/tsconfig.json` had no `exclude`** — ✅ **RESOLVED** (`e29e559`).
   `exclude: ["src/**/*.test.ts", "src/test/**"]` is present, so `npm run build`
   no longer compiles `*.test.ts` into `dist/` or ships tests in the Docker image.
3. **Read-only contract only half-guarded** — ✅ **RESOLVED** (`e29e559`).
   `tools.test.ts` now asserts `tools.length === 19` and none-of-the-forbidden,
   at both the server and HTTP layers.
4. **Tools had zero tests** — ✅ **RESOLVED** (`e29e559`). The Step 13 refactor
   (injectable `MakeClient` in `createMcpServer`/`createMcpRouter`) was done, so
   arg-mapping, PUT semantics, and the two-session JWT-isolation assertion (row
   15) are covered without a live backend.

### Low / to-verify
5. **`z.string().uuid()` vs UUIDv7** (`tools/*.ts`) — ⏳ **OPEN**. The demo
   smoke test exercises real create/get/delete on actual UUIDv7 ids, so a
   rejecting regex would surface there, but there is no focused unit assertion.
   Confirm zod `^3.25` accepts the app's UUIDv7 IDs.
6. **Sessions bound only by `sessionId`** (`mcp/handler.ts`) — ✅ **RESOLVED**.
    Each session now stores the authenticating principal (the backend JWT's `sub`
    claim — the username — falling back to the JWT string when `sub` is absent).
    Every POST/GET/DELETE on an existing session re-checks the caller's principal
    against the stored one and returns 401 on mismatch, so a valid bearer can no
    longer drive another user's transport with a guessed session id. Covered by
    five tests in `mcp/session.test.ts` (cross-principal POST/GET/DELETE → 401,
    owner access preserved, same-principal second token accepted).
7. **`loginToBudgetTracker` couples to `data.token ?? data.accessToken`**
   (`oauth/authorize.ts:64`) — ✅ **RESOLVED in practice**. The real OAuth flow
   in `tool-smoke.ts` now hits the actual backend login shape via live tokens.
8. **`oauth/store.ts` TTLs are hardcoded** (5 min / 24 h) — ⏳ **OPEN** (optional).
   Tests work around it by mutating `expiresAt`; env-configurable TTLs would give
   faster expiry tests.

## Doc hygiene
- `observations.md` (this file) previously asserted several items as missing that
  are now done — refreshed here so it no longer misleads future reviewers.
- Stale React-era documents (`frontend-rewrite-plan.md`,
  `frontend-rewrite-todos.md`) describing a build superseded by the HTMX rewrite
  have been removed; references in `CLAUDE_CONTEXT.md`, `todo.md`,
  `htmx-rewrite-review-concerns.md`, and this file were updated accordingly.

## Suggested order
1. ~~Bind MCP sessions to their authenticating principal (item 6)~~ — done.
2. Add a focused UUIDv7 round-trip test (item 5) and, if desired, env-configurable
   OAuth TTLs (item 8).
