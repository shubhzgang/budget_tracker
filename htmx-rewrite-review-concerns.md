# Code Review Concerns — `feature/htmx-rewrite` vs `master`

Review date: 2026-08-22
Scope: 7 commits, +6,173 / −13,354 across 165 files. React SPA deleted; Spring Boot serves Thymeleaf + htmx 1.9.12 + Alpine 3.14.9. Includes new expenditure-summary feature with stored period totals.

*(Known/accepted: account deletion cascades transactions at DB level without adjusting `expenditure_period_totals` — acknowledged as okay for now.)*

## 1. Expired-session handling likely broken for htmx requests

`AuthEntryPointJwt` redirects non-API paths (302 → `/login?expired=true`). An expired-cookie fragment request follows the redirect via XHR and **swaps the full login page into the fragment target** (e.g. `#transactions-list`). The `app.js` handler only fires on real 401 responses, which page routes no longer produce.

**Fix:** detect the `HX-Request` header in the entry point and return `HX-Redirect` (or 401) instead of a 302.

## 2. CSRF posture is bespoke and partially holey

- The `HX-Request: true` "origin-proof" (`CsrfHeaderFilter`) works only because the CORS config's allowed-header list happens to exclude `HX-Request`. Nothing guards that invariant; one careless `*` in `allowedHeaders` silently disables the check.
- `/login`, `/register`, `/logout` are fully exempt → **login CSRF** (victim logged into attacker's account) and logout CSRF are possible.
- ~~`adjust()` in `ExpenditureSummaryService` is find-then-save with no lock/upsert → concurrent same-period writes can lose updates or violate the `(user_id, period_type, period_key)` unique constraint.~~
  **RESOLVED (2026-08-22):** `adjust()` now uses an atomic PostgreSQL upsert (`INSERT ... ON CONFLICT DO UPDATE SET total = total + delta`) plus a zero-row cleanup; verified under concurrent sessions and via `make test-int`. Note: the unit-test DB (H2) does not support `ON CONFLICT`, so the native-SQL repo tests skip on H2 and run under Postgres-backed runs.

**Suggestion:** either adopt Spring Security's standard CSRF (cookie-repo) or document the rationale and add guard tests asserting cross-origin `HX-Request` requests stay rejected.

## 3. Token / CORS hygiene

- Login/register still return the JWT in the JSON response body despite the HttpOnly-cookie design (token readable by JS anyway).
- `AuthController` keeps `@CrossOrigin(origins = "*")` now that cookies carry authentication.

**Suggestion:** drop the body token unless Bearer clients need it; remove or scope the wildcard CORS annotation.

## 4. `app.cookie.secure` toggle is a footgun

Defaults to true (good), but one property flip silently downgrades production; test properties already set it false.

**Suggestion:** profile-gate it or log a startup warning when false outside dev/test profiles.

## 5. Hardcoded `Asia/Kolkata` in three layers

Constant in Java (`TimeZones.APP_ZONE`), literal in the V2 Flyway SQL backfill, and week/month key formatting duplicated between `TO_CHAR('IYYY-"W"IW')` and `ExpenditurePeriods.weekKey()` — must stay in lockstep forever. Not configurable if timezone needs ever change.

## 6. Unverifiable vendored JavaScript

`htmx.min.js` / `alpine.min.js` committed as stripped minified blobs: no license banner, no version comment, no SRI/hash pinning. htmx 1.9.12 is also an older major line.

## 7. Branch fails its own exit criteria

`frontend-rewrite-todos.md` Step 15 (final full verification + manual sweep) is unchecked; last commit predates completion.

## 8. Code quality (minor)

- Four near-identical 13-param endpoint signatures in `TransactionsViewController`.
- Hand-rolled JSON escaping for `HX-Trigger` while an unused `ObjectMapper` sits nearby.
- Errors returned as HTTP 200 + toast header (loses HTTP semantics).
- Non-UUID path variables produce whitelabel errors.
- `jwtCookie()` duplicated across `AuthController`, `AuthPagesController`, `SecurityConfig`.
- Login POST catches only `BadCredentialsException`; other auth failures → 500.
- Theme cookie value read unvalidated (minor).

## 9. Test coverage regression

~2k lines of frontend unit tests deleted with no JS test runner replacement — the Playwright e2e suite is now the only frontend safety net and cannot cover the edge cases vitest did.

---

## Deployment upgrade verification (master → branch, live simulation)

Simulated a real upgrade: wiped Docker state, deployed master on a fresh volume, seeded data via API (3 accounts, 7 transactions incl. transfer, custom category/label, SQL+CSV backups exported), then deployed this branch over the same volume.

**Results — all verified working:**
- Flyway V2 applies cleanly to existing V1 data; backfilled week/month totals are exact (verified against hand-computed Asia/Kolkata expectations, e.g. W34=2350.50, Jul=75.00).
- All reads identical post-upgrade: accounts / activity / categories / labels / preferences byte-identical vs master baseline.
- Old Bearer tokens remain valid (same JWT secret) — API clients unaffected.
- Writes work on branch build: transaction create/edit/delete and transfers update stored totals correctly at every step.
- Web layer works: `/` redirect logic, unauth → `/login?expired=true`, form login sets HttpOnly cookie, dashboard renders.
- **Rollback is safe:** deploying master back over a V2 database boots fine (Flyway tolerates the applied-but-absent V2; extra table ignored), reads/writes still work.

**Breakage found & fixed during this test — backup import failed on any account with existing totals:**
- `BackupService.restoreBackup` → `ExpenditureSummaryService.recomputeForUser()` used a derived `deleteAllByUserId` (entity-by-entity removes queued in Hibernate's action queue). Hibernate flushes **inserts before deletes**, so re-inserting recomputed rows hit `uq_expenditure_period_totals` while old rows were still present. This is pre-existing on the branch tip (unrelated to the upsert fix) and broke every SQL/CSV restore for live accounts.
- **Fix:** replaced with an immediate bulk JPQL delete (`@Modifying(flushAutomatically = true)`); verified by importing a master-generated SQL backup and CSV round-trip into the upgraded app — restore succeeds, totals/accounts/activity return exactly to baseline.

**Pre-existing quirk confirmed (not a branch regression):** CSV import duplicates transactions when imported into a populated account (master's `importCsv` only adds, never clears); stored totals faithfully reflect the doubled data.
