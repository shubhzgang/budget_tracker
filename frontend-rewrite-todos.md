# Frontend Rewrite — Implementation TODO List

Step-by-step plan to execute `frontend-rewrite-plan.md`. Work on branch `feature/htmx-rewrite` (not master). Rules for every step:
1. Implement the step
2. Run `./gradlew test` (unit) after every backend change
3. Run `make test-int` (integration) after any auth/security change
4. Update affected E2E specs, then run the FULL `make test-e2e` suite after every step (not just affected specs) — the whole suite must stay green
5. Manual check in browser (desktop + mobile viewport, light/dark/OLED)
6. Commit only when the step is fully verified

## Phase 1: HTMX + Thymeleaf Rewrite

### Step 0 — Baseline (before touching anything) — DONE 2026-08-21
- [x] 0.1 Run `./gradlew test` → all green
- [x] 0.2 Run `make test-int` → all green
- [x] 0.3 Run `make test-e2e` → all green. **Baseline: 81 tests passed across 21 spec files (28.8s)**

### Step 1 — Thymeleaf setup + static assets — DONE 2026-08-21
- [x] 1.1 Add `spring-boot-starter-thymeleaf` to `build.gradle` (NOT thymeleaf-extras-springsecurity6)
- [x] 1.2 Create `src/main/resources/templates/` and `templates/fragments/` dirs
- [x] 1.3 Add static assets: `static/js/htmx.min.js` (1.9.12), `static/js/alpine.min.js` (3.14.9), `static/js/app.js` (htmx:responseError → `/login?expired=true` on 401, modal backdrop/escape close), `static/css/style.css` (ported design system: CSS variables for light/dark/OLED + toasts + modal base), `static/js/emojis.json` (1154 emojis generated from React EMOJI_SECTIONS/EMOJI_TAGS, section label as keyword fallback)
- [x] 1.4 Add `data-testid` attributes convention to all templates from the start (E2E selectors must not use Tailwind classes) — convention noted, applied from Step 3 templates onward
- [x] 1.5 Verify: `./gradlew test` green ✓; `make test-e2e` full suite passed ✓ (status: passed, 0 failed)

### Step 2 — Auth: JWT in HttpOnly cookie — DONE 2026-08-21
- [x] 2.1 `AuthController`: login + register set `jwt` cookie (HttpOnly, Lax, path=/, maxAge 86400, secure=false dev) alongside JSON body
- [x] 2.2 `AuthTokenFilter`: fall back to `jwt` cookie after `Authorization: Bearer` header
- [x] 2.3 `AuthEntryPointJwt.commence()`: `/api/*` → 401 JSON; everything else → 302 `/login?expired=true`
- [x] 2.4 `SecurityConfig`: permit `/`, `/login`, `/register`, `/error`, `/css/**`, `/js/**`, `/favicon.ico` before `.anyRequest().authenticated()`
  - **Bug found + fixed:** without `/error` in permitAll, `sendError(401)`/404 forwards to `/error` which re-enters the security chain → entry point fires → clients got 302 instead of 401 JSON. Also fixed pre-existing API 401 behavior. Plan doc updated.
- [x] 2.5 `GET /` controller: `web/LandingController` → redirect to `/dashboard` (authed) or `/login` (unauthed)
- [x] 2.6 Verify: `./gradlew test` ✓; `make test-int` ✓; curl: Set-Cookie on login+register, cookie API auth 200, Bearer compat 200, unauth API 401 JSON, unauth page 302 /login?expired=true, static assets 200, `/` redirect both ways ✓; `make test-e2e` 81/81 ✓

### Step 3 — Base layout (layout.html) — DONE 2026-08-21 (verified via Step 4 login page)
- [x] 3.1 `layout.html` fragments: `theme-toggle` (Alpine select), `desktop-nav` (brand, 3 links w/ active state, theme, user email, logout form), `mobile-header`, `mobile-bottom-nav` (3 icon+label, order T/D/S per React), `fab-modal` (FAB + shared `<dialog>`), `toast-container`, `scripts`. Icons ported from React `Layout.tsx`
- [x] 3.2 Cookie-based theme: `web/PageContextInterceptor` reads `theme` cookie → `data-theme` on `<html>` (default light) + `userEmail` attribute (DB lookup, skipped for /api//css//js); registered in `web/WebConfig`. Alpine toggle writes cookie + attribute
- [x] 3.3 `style.css`: responsive styles incl. FAB (mobile `bottom: 6rem`, desktop `bottom: 2rem; right: 2rem`), bottom nav (fixed, backdrop-blur), auth pages
- [x] 3.4 Note: parameterized `<head th:fragment="head(title)">` fails in Thymeleaf (second `<head>` element drops the signature) → per-page static `<head>` blocks instead. Verified via login page in Step 4: `data-theme=dark` on reload (no FOUC), theme select works
- [x] **Lesson:** always `./gradlew bootJar` before `docker compose up --build` — `./gradlew test` does NOT refresh `build/libs/*.jar`, Docker copies the stale one

### Step 4 — Login + Register + Logout pages — DONE 2026-08-21
- [x] 4.1 `login.html` (Email/Password placeholders, Sign In button, `?expired=true` banner "Your session has expired. Please sign in again.", error box), `register.html` (guarded: redirects to /login when `app.auth.register-enabled=false`)
- [x] 4.2 `web/AuthPagesController`: `GET/POST /login` (BadCredentialsException → re-render with "Bad credentials"), `GET/POST /register` (dup email → error re-render; auto-login cookie on success). **Logout is NOT a controller**: Spring Security's default `LogoutFilter` intercepts `POST /logout` before MVC — configured `.logout(logoutUrl=/logout, addLogoutHandler clears jwt cookie (Max-Age=0), logoutSuccessUrl=/login)` in SecurityConfig
- [x] 4.3 E2E: `playwright.config.ts` baseURL 3300→8811; `auth.spec.ts` register test updated (register page renders when enabled); `theme.spec.ts` class assertions → `data-theme` attribute; 17 specs awaiting pages marked `test.skip(true, "HTMX migration pending: <page>")` (unskip as pages land; Step 11 verifies none remain)
- [x] 4.4 Verify: unit ✓; integration ✓; manual 9/9 (theme cookie→data-theme, expired banner, valid login 302+cookie, bad creds, register page, dup email, logout clears cookie, /dashboard 404 pending Step 5); E2E: **8 passed, 73 skipped, 0 failed** (auth, theme incl. persist-after-reload, cors)

### Step 5 — Dashboard page — DONE 2026-08-21
- [x] 5.1 `DashboardViewController` (`web/`): `GET /dashboard` (full page + model attrs), `GET /dashboard/sections` (refresh target), `GET /dashboard/accounts`, `GET /dashboard/recent` (10 most recent, DESC by transactionDate)
- [x] 5.2 `dashboard.html` + fragments: `dashboard-sections.html`, `account-list.html` (grouped by type, first-appearance order, net worth line), `account-card.html` (edit btn `aria-label="Edit <name>"`, credit-card Debt/utilization bar/limit, friend-lending They-owe-you/You-owe-them), `recent-transactions.html` + `activity-row.html` (icon, title, `MMM d, yyyy` date, account(→toAccount), label chips, signed amount, type label, edit/delete buttons w/ data-kind+data-id)
- [x] 5.3 Account modal CRUD: `GET /accounts/form`, `GET /accounts/{id}/edit`, `POST/PUT/DELETE /accounts`. Success → `account-list` fragment + `HX-Trigger: closeModal + toast-success`. Validation error → form fragment + `HX-Retarget: #modal-content` + field errors. FRIEND_LENDING I_OWE_THEM → `-abs(initialBalance)`; CREDIT_CARD keeps creditLimit else null
- [x] 5.4 Currency formatting: `web/CurrencyFormatter` (en-IN grouping, sign+symbol+abs, per-request instance = thread-safe) exposed as `fmt` + `currencySymbol` by `PageContextInterceptor` (default `₹` from `UserPreferenceService`)
- [x] 5.5 Verify: unit ✓; manual curl/browser 10/10 (dashboard render, create friend+credit-card, net worth incl. credit-card negation, validation error re-target, edit prefill, PUT balance-delta, DELETE, activity row w/ transaction, sections refresh); E2E: unskipped `mobile-logout.spec.ts` + `friend-lending.spec.ts` (Tailwind `div.p-4` → `div[data-testid="account-card"]`); **full suite: 14 passed, 67 skipped, 0 failed**
- [x] **Bugs found + fixed during Step 5:**
  - `CurrencyFormatter` was `@Component` with constructor arg → startup failure; removed annotation (instantiated manually per request)
  - `th:each` + `th:replace` on the SAME element drops the loop variable → wrap: `<div th:each=...><div th:replace=.../></div>`
  - `T(Math)` static access forbidden in Thymeleaf SPEL → ternary instead
  - Stray unclosed `<th:each>` element silently truncated the whole rendered page
  - `#temporals.format(date, pattern, 'en-US')` — 3rd String arg is a TIMEZONE not locale → 2-arg form (container `LANG=en_US.UTF-8` ⇒ English month names)
  - `${expr} + fmt.format(...)` — trailing content after `}` must be inside one `${}` (literal `'%'` after `}` is OK)
  - `ActivityResponse.type`/`kind` are Strings (not enums) → no `.name()`
  - **Stale-form race (E2E failure):** modal opened synchronously while form loaded async → second open showed previous form; test/user filled stale form, then swap wiped it and submit was lost. Fix: `openFormModal()` clears `#modal-content` before `showModal()` (Add Account btn, FAB, `openAccountEdit`, `openItemEdit`)
  - Full page (`GET /dashboard`) must set the same model attrs as the fragments — initial render showed empty state otherwise

### Step 6 — Transactions page — DONE 2026-08-21
- [x] 6.1 `TransactionsViewController`: `GET /transactions` (full page), `GET /transactions/list` (fragment: search, type, accountId, startDate, endDate, page). Filter params are defensive Strings (htmx sends `accountId=`/`type=` empty on clear) → `parseUuid`/`parseLocalDate`/`blankToNull` (empty/invalid → null; JPQL needs NULL, `""` matches nothing)
- [x] 6.2 `transactions.html` + `transaction-list.html` (infinite scroll sentinel, `hx-swap="outerHTML"` on sentinel, no hx-push-url on sentinel) + `activity-row.html` (E2E contract: `div.group`, `p.font-semibold`, `span.bg-accent` chips, exact `title="Edit"`/`title="Delete"`, aria-label w/ description, `+₹X adj` badge only when > 0)
- [x] 6.3 Filter bar: search (debounce 300ms), type select, account select (server-populated, resets to page 0), startDate, endDate — every input's `hx-include` references ALL other filters. NO `hx-push-url` on filters (fragment requests push the wrong URL)
- [x] 6.4 CRUD: `POST /transactions`, `PUT /transactions/{id}`, `DELETE /transactions/{id}` (transactions + transfers, `fromAccountId` alias for the Alpine-renamed field) → HX-Trigger toast + list refresh. `ResponseEntity<String>` returns (`""` body = view-name 500)
- [x] 6.5 `transaction-form.html`: preference defaults (accountId, type, categoryId, default label pre-selected, falls back to first label), edit form pre-populated (`model.addAttribute("item", item)` — was missing, edit rendered truncated); `th:value="${formAmount}"` via `formNumber()` (strips trailing zeros so the 3-way calc sees "100" not "100.00"); `novalidate` (hidden `required` transfer fields blocked native submit)
- [x] 6.6 Inline category creation: "+ New category…" option → Alpine inline row (name + emoji input + Add/Cancel) → `fetch()` POST `/api/v1/categories` → append `<option>` auto-selected; duplicate-name error. (Last inline-category E2E test needs Settings > Categories page → stays skipped for Step 8)
- [x] 6.7 Multi-label Alpine picker (checkbox dropdown, chips, hidden `labelIds[]` inputs, pre-selected on edit; `const labels = /*[[${labels}]]*/ []`)
- [x] 6.8 FAB opens `/transactions/form` modal from any page; `lastCategoryId` remembered across open/close
- [x] 6.9 Verify: E2E `transactions`, `transaction-input`, `account-filter`, `transfer-filter`, `transaction-placeholder`, `edit-flow`, `delete-transaction`, `validation` unskipped + green (`multi-label`/`emoji-keyword-search` stay skipped — need Settings label creation, Step 8). Unit ✓. **Full suite: 41 passed, 0 failed, 40 skipped** (multi-label + emoji-keyword-search unskipped & green in Step 8)
- [x] **Bugs found + fixed during Step 6:**
  - **Alpine 3 `this` in inline handlers**: inside `@change`/`@input`, `this` = the DOM element, NOT the component → `this.$el` was the select, not the form. Store `this.formEl = this.$el` in `init()` and use it in all methods
  - **htmx 1.9 HX-Trigger payload**: `event.detail` is `{value, elt}`, not a string → toast listeners unwrap via `triggerValue()` (`typeof detail === 'string' ? detail : detail.value`)
  - **Stale-filter race**: two filter changes in flight → older response swaps in last, clobbering the newer filter. Fix: `htmx:beforeRequest` listener aborts the previous request whose target is `#transactions-list` (detail = `{xhr, target, requestConfig, ...}`)
  - **Dialog `close` event is async** (fires as a task, ~15ms after `dialog.close()`) → clearing `#confirm-message` in the `close` listener lost the race with Playwright's same-tick strict-mode text lookup. Fix: clear title/message synchronously inside `closeConfirmDialog()` (backdrop + Cancel both route through it); same pattern clears `#modal-content` on form-modal close
  - `th:text="'+' + fmt.format(...)"` without `${...}` wrapper → literal text in output; SPEL must be one `${}` expression
  - Spring `@RequestParam UUID` 400s on htmx's empty-string params → defensive String params + manual parsing (see 6.1)
  - `Lombok` boolean getter: `l.isDefault()` not `l.getDefault()`; `OffsetDateTime` needs no `.toOffsetDateTime()`

### Step 7 — Transfers — DONE 2026-08-21 (built incrementally during Step 6)
- [x] 7.1 Transfer form fragment: from-account, to-account, 3-way amount calc (Alpine: fromAmount/toAmount/adjustment — `onAmountInput` auto-fills the third). Single `transaction-form.html` carries both variants; transfer fields hidden for EXPENSE/INCOME via `applyTypeUI()`
- [x] 7.2 `GET /transfers/form` (alias of create form), `GET /transfers/{id}/edit` (prefills from/to amount + adjustment, `hx-put="/transfers/{id}"`), `POST/PUT/DELETE /transfers`. Form submits `fromAccountId` (Alpine-renamed) → controller accepts it as alias of `accountId`
- [x] 7.3 Type toggle: single form + client-side Alpine show/hide (NOT htmx.ajax fragment swap — simpler, same observable behavior; E2E `transaction-input`/`transfer-filter` verify it). to-account options exclude the selected from-account
- [x] 7.4 Verify: E2E `transfer-filter` (19 w/ related), `transaction-input` (3-way calc), `friend-lending` green; `update-transaction-transfer` stays skipped (needs Settings label/category creation, Step 8); manual: transfer edit prefill verified via curl (`value="300"`, hx-put url, to-account selected)

### Step 8 — Settings page
- [x] 8.1 `SettingsViewController`: `GET /settings` (full page with tabs), `GET /settings/categories`, `GET /settings/labels`, `GET /settings/defaults` (label "Defaults"), `GET /settings/data` (label "Data & Backup")
- [x] 8.2 Fragments: `category-manager.html`, `label-manager.html` (CRUD with emoji/icon), `defaults-form.html` (currency symbol, theme, default account/type/category/label), `backup-manager.html`
- [x] 8.3 Category/label CRUD routes (reuse or mirror API services) with validation errors rendered in-form (pipe pre-validation matches React message "Label name cannot contain '|'")
- [x] 8.4 Verify: E2E `category.spec.ts`, `preferences.spec.ts`, `multi-label`, `emoji-keyword-search` green; settings tab toggle replicates React conditional mounting (inactive panels in `<template data-panel-src>`, cloned on activation); emoji picker with section tabs + keyword search + outside-click close

### Step 9 — Backups
- [x] 9.1 `BackupsViewController` history table w/ download buttons (spec requires `button` role); fixed `ContentDisposition` builder (`.build().toString()`) and `th:attr` nested-`@{}` 500
- [x] 9.2 `POST /backups/export?format=SQL|CSV` (HX-Trigger + refresh list), `POST /backups/import` (`hx-encoding="multipart/form-data"`, `hx-trigger="change"` on the file input — `change` doesn't bubble so form-level trigger never fires)
- [x] 9.3 `GET /backups/{id}/download` (binary, Content-Disposition attachment), `DELETE /backups/clear` (hx-confirm)
- [x] 9.4 `confirm-dialog.html` fragment for destructive actions
- [x] 9.5 Verify: E2E `backup.spec.ts` green (SQL + CSV full round-trips); transfer-edit fixes: chip × removed from toggle (Playwright center-click hit it), disabled `type` select no longer sent → `type` now `required=false` in `updateTransfer`

**Full suite after Steps 8+9: 81 passed, 0 failed, 0 skipped; `./gradlew test` green.**

### Step 10 — Polish: toasts + validation + edge cases
- [x] 10.1 Toasts everywhere (success + error) — implemented via HX-Trigger headers on all 4 web controllers + `showToast()` in app.js with 3s auto-dismiss (equivalent UX to the planned OOB swap, no extra fragment needed)
- [x] 10.2 Validation error rendering: account form uses `@Valid` + `BindingResult` → `formErrors()` re-renders form with field errors (HX-Retarget #modal-content); transaction form has React-parity client-side alerts (amount > 0, 3-way transfer calc, destination account, inline category); service-level failures → `toast-error`
- [x] 10.3 Verify: E2E `validation.spec.ts`, `delete-transaction.spec.ts`, `cors.spec.ts` green (part of full suite: 81 passed, 0 failed, 0 skipped)

### Step 11 — Remove React frontend
- [x] 11.1 Delete `frontend/` directory
- [x] 11.2 `docker-compose.yml`: remove frontend service (Spring Boot serves everything); expose backend on host port 8080 (replaces old nginx:3300 entry point)
- [x] 11.3 `Dockerfile`: confirm static assets (css/js/emojis.json) are in the jar
- [x] 11.4 `Makefile`: drop `build-frontend` from `test-e2e`/`run-stack`/`run-demo` + remove port-3300 wait
- [x] 11.5 Verify: `make run-stack` → full app on Spring Boot port only (login page + all static assets on :8080, only postgres+backend containers); `make test-e2e` ALL specs green (81 passed); `make test-int` green

**Flake fix found during 11.5:** `multi-label.spec.ts` transfer test failed ~1/3 of full-suite runs — `findAllByUserId` had no ORDER BY, so under parallel test load the account list order flipped and the "first account" fallback (`accounts.get(0)`, matching React's `accounts[0]?.id`) intermittently pre-selected the wrong from-account, which `updateToAccountOptions()` then excluded from the to-account options. Fixed with `findAllByUserIdOrderByIdAsc` (UUIDv7 = time-ordered = deterministic creation order).

## Phase 2: Expenditure Dashboard

### Step 12 — Spending limits (Flyway + entity)
- [ ] 12.1 `V2__add_spending_limits.sql`: add nullable `weekly_limit`, `monthly_limit` to `user_preferences`
- [ ] 12.2 `UserPreference` entity: add fields; update DTO/request in `UserPreferenceController` if limits are settable
- [ ] 12.3 Verify: `./gradlew test` + `make test-int` green (migration runs on fresh DB); manual: set limits via settings defaults form (add fields there in Step 12.4)
- [ ] 12.4 `defaults-form.html`: weekly/monthly limit inputs

### Step 13 — expenditure-summary endpoint
- [ ] 13.1 `ExpenditureSummaryResponse` DTO (6 BigDecimal fields)
- [ ] 13.2 `TransactionRepository`: native query, conditional aggregation, `type IN ('EXPENSE','LEND')`, ISO weeks
- [ ] 13.3 `TransactionService.getExpenditureSummary()` + `GET /api/v1/transactions/expenditure-summary`
- [ ] 13.4 Unit test (mock repo), `@WebMvcTest` for endpoint, `@DataJpaTest` with data across periods (today, yesterday, week boundaries, month boundaries, first day of week/month, empty)
- [ ] 13.5 Verify: `./gradlew test` green; curl endpoint against demo data, spot-check totals

### Step 14 — Period cards UI
- [ ] 14.1 `fragments/period-cards.html`: 6 cards (Yesterday, Today, Last Week, This Week, Last Month, This Month) linking to `/transactions?startDate=...&endDate=...`; progress bar on This Week / This Month when limit set ("₹3,200 of ₹5,000")
- [ ] 14.2 `dashboard.html`: include period cards above transaction list
- [ ] 14.3 `DashboardViewController`: fetch summary + preferences, pass to template
- [ ] 14.4 Verify: `make run-demo` → cards show seeded totals; click each card → transactions list filtered correctly; limit over/under display correct; E2E still green (`make test-e2e`)

### Step 15 — Final full verification
- [ ] 15.1 `./gradlew test` green
- [ ] 15.2 `make test-int` green
- [ ] 15.3 `make test-e2e` — all specs green
- [ ] 15.4 Manual sweep: all pages desktop + mobile, all 3 themes, auth flow (login/expired/logout), FAB, modals, toasts, backups, period cards
- [ ] 15.5 Update `todo.md` with completion notes
