# Frontend Rewrite & Expenditure Dashboard Feature Plan

## Current Architecture Summary

| Layer | Technology | Notes |
|---|---|---|
| Backend | Java 21, Spring Boot 3.5, JPA, PostgreSQL 18, Flyway | Well-structured, proper pagination via Spring Data `Page` |
| Frontend | React 19 + Vite 8 + TypeScript 6 + Tailwind 4 + Recharts 3 | **Heavyweight for the use case** |
| Auth | JWT (Bearer token in localStorage) → migrating to HttpOnly cookie | See Phase 1 auth section |
| API | `/api/v1/*` prefix, ~9 controllers | RESTful with DTOs in some places |
| Deployment | Docker Compose (Postgres + Backend + Nginx-served frontend) | Clean |

The frontend has **~28 React components** across 4 pages (Login, Dashboard, Transactions, Settings) with **5 Context providers** (Auth, Preferences, Theme, Toast, UI). Most pages follow the same pattern: `useEffect` → fetch data → `useState` → render.

---

## Decisions Made

| Question | Decision |
|---|---|
| Frontend direction | **Rewrite in HTMX + Thymeleaf first**, then build the expenditure feature on the new stack |
| Week definition | **ISO weeks (Monday–Sunday)** — PostgreSQL's `date_trunc('week', ...)` default |
| Transfers in expenditure? | **No** — only `EXPENSE` and `LEND` type transactions count |

---

## Phase 1: HTMX + Thymeleaf Frontend Rewrite

### Why HTMX?

The current React frontend uses ~200KB+ of minified JS (React + ReactDOM + Router + Recharts + Axios) for what is fundamentally a fetch-render-form app. HTMX is ~14KB, returns pre-rendered HTML fragments from the server, and eliminates client-side state management entirely.

| Concern | Current (React) | New (HTMX + Thymeleaf) |
|---|---|---|
| Bundle size | ~200KB+ minified JS | ~14KB (HTMX) + ~15KB (Alpine.js) |
| Data flow | JSON → JS objects → Virtual DOM → Real DOM | Server renders HTML → HTMX swaps into DOM |
| State management | 5 React Contexts, many useState hooks | Server is single source of truth |
| Auth | JWT in localStorage + Axios interceptor | Session-based (Spring Security default) — cookies sent automatically |
| CSRF | Disabled (stateless JWT) | Enabled — HTMX configured to send CSRF token via `htmx:configRequest` |
| Build step | Vite + TypeScript compilation + Tailwind PostCSS | None — Thymeleaf templates served directly |
| Charts | Recharts (Pie + Bar) | **Dropped** — not migrating charts for now |
| Search/filter | Client-side state + re-fetch | `hx-get` with `hx-trigger="input changed delay:300ms"` |
| Modals | React state + Portal | `hx-get="/fragment" hx-target="#modal"` |
| Infinite scroll | React state + "Load More" button | Sentinel element with `hx-trigger="revealed"` + `hx-swap="outerHTML"` |
| Toast notifications | React Context + setTimeout | Alpine.js `x-data` + CSS animations + OOB swaps |
| Theme toggle | React Context + CSS variables + localStorage | Cookie-based theme (server reads cookie → renders correct class, no FOUC) |
| 3-way transfer calc | React useState + useEffect | Alpine.js `x-data` with computed properties |

### Backend Changes for Phase 1

#### Add Thymeleaf dependency

```groovy
// build.gradle
implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
implementation 'org.thymeleaf.extras:thymeleaf-extras-springsecurity6'
```

#### Auth: JWT in HttpOnly Cookie

Keep a **single** stateless `SecurityFilterChain`. Instead of form login + sessions, switch the token transport from `localStorage` to a **secure HttpOnly cookie**. This eliminates the need for form login, CSRF tokens on API calls, and dual auth chains.

**Login endpoint change:** `POST /api/v1/auth/login` already returns a JWT. Add a response cookie alongside the JSON body:

```java
// In AuthController.java login method
ResponseCookie jwtCookie = ResponseCookie.from("jwt", jwt)
    .httpOnly(true)
    .secure(false)  // set true in production
    .path("/")
    .maxAge(86400)  // 24 hours
    .sameSite("Lax")
    .build();
return ResponseEntity.ok()
    .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
    .body(new JwtResponse(jwt, ...));
```

**AuthTokenFilter update:** Check the `jwt` cookie as a fallback after the `Authorization` header:

```java
String jwt = parseJwtFromHeader(request);  // existing logic
if (jwt == null) {
    jwt = parseJwtFromCookie(request);     // new: fallback to cookie
}
```

**Landing page redirect:** Add a simple `GET /` redirect to `/dashboard` for authenticated users, `/login` for unauthenticated users. No form login needed.

Key points:
- Single `SecurityFilterChain` — no `@Order` tricks, no dual auth
- JWT in cookie means no `localStorage` access, no Axios interceptor, no client-side token handling
- `/api/v1/**` endpoints continue to accept `Authorization: Bearer` header (for MCP/programmatic access)
- Thymeleaf pages read JWT from cookie automatically — browser sends it with every request
- CSRF must be disabled (stateless, no session) — but cookie is HttpOnly so XSS can't steal it
- `AuthContext` ThreadLocal pattern unchanged — still populated by `AuthTokenFilter`

#### CSRF Strategy

With JWT in HttpOnly cookie (stateless, no session), CSRF is **disabled** — same as the current API. The cookie is HttpOnly so XSS cannot read the token. For any future session-based features, CSRF can be added at that point.

#### New Thymeleaf Controllers

Create a new set of controllers that return Thymeleaf views/fragments instead of JSON. These live alongside the existing API controllers:

| Route | Purpose | Returns |
|---|---|---|
| `GET /login` | Login page | Full page |
| `POST /login` | Form login → sets JWT cookie | Redirect to `/dashboard` |
| `GET /dashboard` | Dashboard page | Full page |
| `GET /dashboard/accounts` | Accounts section fragment | HTML fragment |
| `GET /dashboard/recent` | Recent transactions fragment | HTML fragment |
| `GET /transactions` | Transactions page | Full page |
| `GET /transactions/list` | Transaction/activity list fragment (paginated, filtered) | HTML fragment |
| `GET /transactions/form` | Transaction create form | HTML fragment (for modal) |
| `GET /transactions/{id}/edit` | Transaction edit form | HTML fragment (for modal) |
| `POST /transactions` | Create transaction | HX-Trigger header + redirect/swap |
| `PUT /transactions/{id}` | Update transaction | HX-Trigger header + redirect/swap |
| `DELETE /transactions/{id}` | Delete transaction | HX-Trigger header |
| `GET /transfers/form` | Transfer create form | HTML fragment (for modal) |
| `GET /transfers/{id}/edit` | Transfer edit form | HTML fragment (for modal) |
| `POST /transfers` | Create transfer | HX-Trigger header + redirect/swap |
| `PUT /transfers/{id}` | Update transfer | HX-Trigger header + redirect/swap |
| `DELETE /transfers/{id}` | Delete transfer | HX-Trigger header |
| `GET /accounts/form` | Account create form | HTML fragment (for modal) |
| `GET /accounts/{id}/edit` | Account edit form | HTML fragment (for modal) |
| `POST /accounts` | Create account | HX-Trigger header + redirect/swap |
| `PUT /accounts/{id}` | Update account | HX-Trigger header + redirect/swap |
| `DELETE /accounts/{id}` | Delete account | HX-Trigger header |
| `GET /settings` | Settings page | Full page |
| `GET /settings/categories` | Categories tab fragment | HTML fragment |
| `GET /settings/labels` | Labels tab fragment | HTML fragment |
| `GET /settings/preferences` | Preferences tab fragment | HTML fragment |
| `GET /settings/backup` | Backup tab fragment | HTML fragment |

#### Keep JSON API

The existing `/api/v1/*` JSON endpoints remain unchanged for:
- MCP/programmatic access
- Future mobile app or third-party integration
- Backup import/export (binary data)

### Frontend Structure (Thymeleaf Templates)

```
src/main/resources/
├── templates/
│   ├── layout.html              # Base layout (nav, footer, HTMX/Alpine/CSRF includes)
│   ├── login.html               # Login page
│   ├── dashboard.html           # Dashboard page
│   ├── transactions.html        # Transactions page
│   ├── settings.html            # Settings page
│   └── fragments/
│       ├── account-card.html    # Account balance card
│       ├── account-form.html    # Account create/edit form (modal content)
│       ├── account-list.html    # Grouped account cards
│       ├── transaction-card.html # Single transaction/activity row
│       ├── transaction-list.html # Paginated transaction list
│       ├── transaction-form.html # Transaction/transfer create/edit form
│       ├── category-manager.html # Category CRUD
│       ├── label-manager.html   # Label CRUD
│       ├── preference-form.html # Preferences form
│       ├── backup-manager.html  # Backup section
│       ├── confirm-dialog.html  # Delete confirmation modal
│       ├── toast.html           # Toast notification (OOB swap)
│       └── period-cards.html    # Expenditure period cards (Phase 2)
├── static/
│   ├── css/
│   │   └── style.css           # All styles (replaces Tailwind + index.css)
│   └── js/
│       ├── htmx.min.js         # HTMX library (~14KB)
│       ├── alpine.min.js       # Alpine.js for client-side reactivity (~15KB)
│       └── app.js              # Minimal custom JS (CSRF config, theme cookie sync)
```

> **Note:** Charts (Recharts/Chart.js) are **not included** in this migration. Analytics/spending charts will be added back in a future phase if needed.

### Key HTMX Patterns to Use

#### Search with debounce
```html
<input type="search" name="search"
       hx-get="/transactions/list"
       hx-trigger="input changed delay:300ms"
       hx-target="#transaction-list"
       hx-include="[name='type'], [name='accountId']"
       placeholder="Search transactions...">
```

#### Account filter with pagination reset

The account filter dropdown is populated server-side on page load. When the selection changes, it resets pagination to page 0:

```html
<select name="accountId"
        hx-get="/transactions/list"
        hx-trigger="change"
        hx-target="#transaction-list"
        hx-include="[name='search'], [name='type']">
  <option value="">All Accounts</option>
  <option th:each="a : ${accounts}" th:value="${a.id}" th:text="${a.name}"></option>
</select>
```

The first request to `/transactions/list?accountId=X&page=0` starts fresh. The sentinel incrementally adds pages from there. Changing the filter triggers a full swap (page 0), so the old sentinel is replaced.

#### Infinite scroll

Use `hx-swap="outerHTML"` on a **sentinel element** that replaces itself with the next page's rows plus a new sentinel for the following page. This correctly chains pagination:

```html
<div id="transaction-list">
  <!-- rendered rows from page 0 -->
  <tr th:each="item : ${items}">...</tr>

  <!-- Sentinel: replaces itself with page 1 rows + new sentinel for page 2 -->
  <div th:if="${hasMore}"
       hx-get="/transactions/list?page=1"
       hx-trigger="revealed"
       hx-swap="outerHTML"
       class="loading-sentinel">
    Loading more...
  </div>
</div>
```

The server response for page N includes:
```html
<!-- Page N rows -->
<tr>...</tr>
<tr>...</tr>
<!-- New sentinel for page N+1 (only if hasMore) -->
<div th:if="${hasMore}"
     hx-get="/transactions/list?page=N+1"
     hx-trigger="revealed"
     hx-swap="outerHTML">
  Loading more...
</div>
```

When there are no more pages, the sentinel is simply not included — pagination stops naturally.

#### Modal forms
```html
<button hx-get="/accounts/form"
        hx-target="#modal-content"
        hx-swap="innerHTML"
        onclick="document.getElementById('modal').showModal()">
  + Add Account
</button>

<dialog id="modal">
  <div id="modal-content"></div>
</dialog>
```

#### Toast notifications (out-of-band swap)
```html
<!-- Server response includes this alongside the main response -->
<div id="toast-container" hx-swap-oob="beforeend">
  <div class="toast toast-success" x-data="{ show: true }"
       x-init="setTimeout(() => show = false, 3000)"
       x-show="show" x-transition>
    Account created successfully!
  </div>
</div>
```

#### Theme toggle (Cookie-based, no FOUC)

Storing theme in `localStorage` causes a Flash of Unstyled Content (FOUC) on SSR pages — the server renders the default theme, then Alpine.js swaps it client-side, causing a visible flicker.

**Fix:** Store the theme in a **cookie**. The server reads the cookie during rendering and injects the correct `data-theme` attribute into the `<html>` tag. No flicker.

**Server side** — Spring interceptor or Thymeleaf inline:
```html
<!-- layout.html -->
<html th:attr="data-theme=${#request.getCookies() != null && #request.getCookies().theme != null ? #request.getCookies().theme.value : 'light'}">
```

Or use a `HandlerInterceptor` to read the cookie and set a model attribute:
```java
@Component
public class ThemeInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String theme = "light";
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("theme".equals(c.getName())) { theme = c.getValue(); break; }
            }
        }
        request.setAttribute("theme", theme);
        return true;
    }
}
```
```html
<html th:attr="data-theme=${theme}">
```

**Client side** — Alpine.js reads from cookie and writes to cookie on change:
```html
<div x-data="{
  theme: document.documentElement.getAttribute('data-theme') || 'light',
  setTheme(t) {
    this.theme = t;
    document.documentElement.setAttribute('data-theme', t);
    document.cookie = 'theme=' + t + ';path=/;max-age=31536000;SameSite=Lax';
  }
}">
  <select x-model="theme" @change="setTheme(theme)">
    <option value="light">Light</option>
    <option value="dark">Dark</option>
    <option value="oled">OLED</option>
  </select>
</div>
```

The initial `x-data` reads from the already-correct `data-theme` attribute (set server-side from the cookie), so there's zero flicker.

#### 3-way transfer calculation (Alpine.js)
```html
<div x-data="{
  fromAmount: '',
  toAmount: '',
  adjustment: '',
  lastEdited: [],
  updateField(field) {
    this.lastEdited = [...this.lastEdited.filter(f => f !== field), field].slice(-2);
    if (this.lastEdited.length === 2 && this.fromAmount && (this.toAmount || this.adjustment)) {
      const from = parseFloat(this.fromAmount) || 0;
      const to = parseFloat(this.toAmount) || 0;
      const adj = parseFloat(this.adjustment) || 0;
      if (!this.lastEdited.includes('fromAmount')) this.fromAmount = (to - adj).toFixed(2);
      else if (!this.lastEdited.includes('toAmount')) this.toAmount = (from + adj).toFixed(2);
      else if (!this.lastEdited.includes('adjustment')) this.adjustment = (to - from).toFixed(2);
    }
  }
}">
  <input type="number" x-model="fromAmount" @input="updateField('fromAmount')">
  <input type="number" x-model="adjustment" @input="updateField('adjustment')">
  <input type="number" x-model="toAmount" @input="updateField('toAmount')">
</div>
```

#### Emoji picker (Alpine.js)

Emoji data stored as a static JSON file (`/js/emojis.json`) mapping keywords → emoji characters. Alpine.js component with instant client-side filtering:

```html
<div x-data="{
  search: '',
  emojis: [],
  selected: '',
  filtered() { return this.search ? this.emojis.filter(e => e.keywords.some(k => k.includes(this.search.toLowerCase()))) : this.emojis.slice(0, 60); }
}" x-init="fetch('/js/emojis.json').then(r => r.json()).then(d => emojis = d)">
  <input type="text" x-model="search" placeholder="Search emoji...">
  <div class="emoji-grid">
    <button th:each="e : ${filtered()}" @click="selected = e.emoji" th:text="${e.emoji}"></button>
  </div>
  <input type="hidden" name="icon" x-model="selected">
</div>
```

#### Currency formatting (server-side)

Instead of React's `PreferenceContext` formatting client-side, pass `currencySymbol` to every template via a model attribute (e.g., from a `HandlerInterceptor` or by injecting `UserPreferenceService` into each controller). All money values are formatted server-side using Thymeleaf:

```html
<!-- In template -->
<span th:text="${currencySymbol} + ' ' + ${#numbers.formatDecimal(account.balance, 0, 'COMMA', 2, 'POINT')}">₹ 1,250.00</span>
```

Or create a Thymeleaf utility (`#currency.format(amount)`) registered as a dialect bean for cleaner usage across all templates.

#### Validation error rendering

When a form submission fails server-side validation (`@Valid`), the controller returns the same form fragment with error messages injected:

```java
@PostMapping("/accounts")
public String createAccount(@Valid @ModelAttribute AccountForm form, BindingResult result, Model model) {
    if (result.hasErrors()) {
        model.addAttribute("errors", result.getFieldErrors());
        return "fragments/account-form";  // returns form HTML with errors
    }
    accountService.create(form);
    model.addAttribute("toast", "Account created!");
    return "fragments/account-form :: #form";  // close modal + toast
}
```

```html
<!-- In account-form.html -->
<input type="text" name="name" th:value="${form.name}">
<div th:if="${errors?.getFieldError('name')}"
     th:text="${errors.getFieldError('name').defaultMessage}"
     class="text-red-500 text-sm"></div>
```

#### Transaction/transfer form toggle

Single form that switches fields based on transaction type selection:

```html
<select name="type" x-model="type" @change="type === 'TRANSFER' ? htmx.ajax('GET', '/transfers/form', '#form-content') : htmx.ajax('GET', '/transactions/form', '#form-content')">
  <option value="INCOME">Income</option>
  <option value="EXPENSE">Expense</option>
  <option value="LEND">Lend</option>
  <option value="BORROW">Borrow</option>
  <option value="TRANSFER">Transfer</option>
</select>
<div id="form-content">
  <!-- Replaced by HTMX with the correct form fragment -->
</div>
```

Selecting "Transfer" swaps in the transfer form (with to-account, fromAmount, toAmount, adjustment). Selecting any transaction type swaps in the transaction form (with single account, amount). Both fragments are self-contained and handle their own submissions.

### Migration Approach

1. **Keep both frontends running** during migration — the React frontend at its current path, HTMX frontend served by Spring Boot directly
2. Add Thymeleaf dependency and cookie-based JWT auth to `SecurityConfig`
3. Migrate page by page: Login → Dashboard → Transactions → Settings
4. Once all pages are migrated and verified with E2E tests, remove the `frontend/` directory and its Docker container
5. Update `docker-compose.yml` to remove the frontend service (Spring Boot serves everything)
6. Update `Dockerfile` to include static assets (CSS, JS, emoji JSON) in the Spring Boot jar

### What Gets Deleted After Migration

```
frontend/                    # Entire React frontend directory
├── src/                     # All React components, contexts, pages, types
├── package.json             # React, Vite, Tailwind, Recharts dependencies
├── vite.config.ts
├── tailwind.config.js
├── tsconfig*.json
├── Dockerfile               # Separate frontend Docker image
└── ...
```

The `docker-compose.yml` frontend service also gets removed. E2E tests in `e2e/` are preserved — they test user flows against the rendered HTML, not the implementation.

---

## Phase 2: Expenditure Dashboard (on HTMX stack)

> **Note:** No charts of any kind in this phase. Spending limits stored on `UserPreference` entity. Period cards show totals as styled HTML; clicking a card filters the transaction list by that date range.

### Backend — New Endpoint

#### New: `GET /api/v1/transactions/expenditure-summary`

Returns expenditure totals for 6 time periods in a single response:

```json
{
  "today": 430.00,
  "yesterday": 1250.00,
  "currentWeek": 3200.00,
  "lastWeek": 8750.00,
  "currentMonth": 15800.00,
  "lastMonth": 42500.00
}
```

**Implementation:** Single native SQL query with conditional aggregation against the indexed `transaction_date` column. Only counts `EXPENSE` and `LEND` type transactions:

```sql
SELECT
  COALESCE(SUM(CASE WHEN transaction_date >= date_trunc('day', NOW()) THEN amount ELSE 0 END), 0) as today,
  COALESCE(SUM(CASE WHEN transaction_date >= date_trunc('day', NOW()) - INTERVAL '1 day'
               AND transaction_date < date_trunc('day', NOW()) THEN amount ELSE 0 END), 0) as yesterday,
  COALESCE(SUM(CASE WHEN transaction_date >= date_trunc('week', NOW()) THEN amount ELSE 0 END), 0) as current_week,
  COALESCE(SUM(CASE WHEN transaction_date >= date_trunc('week', NOW()) - INTERVAL '7 days'
               AND transaction_date < date_trunc('week', NOW()) THEN amount ELSE 0 END), 0) as last_week,
  COALESCE(SUM(CASE WHEN transaction_date >= date_trunc('month', NOW()) THEN amount ELSE 0 END), 0) as current_month,
  COALESCE(SUM(CASE WHEN transaction_date >= date_trunc('month', NOW()) - INTERVAL '1 month'
               AND transaction_date < date_trunc('month', NOW()) THEN amount ELSE 0 END), 0) as last_month
FROM transactions
WHERE user_id = :userId AND type IN ('EXPENSE', 'LEND')
```

Week boundaries use **ISO weeks (Monday start)** — PostgreSQL's `date_trunc('week', ...)` default.

#### Spending Limits

Add two fields to the existing `UserPreference` entity:

```java
@Entity
@Table(name = "user_preferences")
public class UserPreference extends BaseEntity {
    // ... existing fields (currencySymbol, theme) ...
    
    @Column(name = "weekly_limit")
    private BigDecimal weeklyLimit;
    
    @Column(name = "monthly_limit")
    private BigDecimal monthlyLimit;
}
```

Requires a Flyway migration to add the columns. Limits are optional (null = no limit set). The period cards compare actual spending against the relevant limit, showing a progress indicator (e.g., "₹3,200 of ₹5,000" with a fill bar).

#### Rollover

**No rollover** — each period resets. Budget is a fixed monthly/weekly amount. Underspent amount does not carry forward.

#### Files to create/modify:

| File | Change |
|---|---|
| `ExpenditureSummaryResponse.java` | **[NEW]** DTO with 6 `BigDecimal` fields |
| `TransactionRepository.java` | **[MODIFY]** Add `@Query(nativeQuery=true)` method |
| `TransactionService.java` | **[MODIFY]** Add `getExpenditureSummary()` method |
| `TransactionController.java` | **[MODIFY]** Add `GET /expenditure-summary` endpoint |
| `UserPreference.java` | **[MODIFY]** Add `weeklyLimit`, `monthlyLimit` fields |
| `V2__add_spending_limits.sql` | **[NEW]** Flyway migration for new columns |

### Frontend — Dashboard Period Cards (Thymeleaf + HTMX)

A row of clickable period cards rendered as a Thymeleaf fragment:

```
┌─────────────┐ ┌─────────────┐ ┌──────────────┐ ┌─────────────┐ ┌──────────────┐ ┌─────────────┐
│  Yesterday  │ │   Today     │ │  Last Week   │ │ This Week   │ │  Last Month  │ │ This Month  │
│  ₹1,250.00  │ │  ₹430.00    │ │  ₹8,750.00   │ │ ₹3,200.00   │ │  ₹42,500.00  │ │ ₹15,800.00  │
└─────────────┘ └─────────────┘ └──────────────┘ └─────────────┘ └──────────────┘ └─────────────┘
                                                    ▲ active
```

Clicking a card uses `hx-get` to re-fetch the transaction list below with the corresponding date range (`startDate`/`endDate` query params), showing only transactions from that period. No charts — just a filtered, styled list of transactions.

When a weekly/monthly limit is set on `UserPreference`, the "This Week" and "This Month" cards also show a progress bar comparing actual spend against the limit.

#### Files to create/modify:

| File | Change |
|---|---|
| `fragments/period-cards.html` | **[NEW]** Period cards Thymeleaf fragment with limit progress bars |
| `dashboard.html` | **[MODIFY]** Include period cards section above transaction list |
| `DashboardViewController.java` | **[MODIFY]** Fetch expenditure summary + user preferences, pass to template |

---

## Verification Plan

### Phase 1 (HTMX Rewrite)
- **Per-page manual verification**: After migrating each page, visually compare with the React version
- **Playwright E2E tests**: Run `make test-e2e` — existing E2E tests should pass against the new frontend (they test user flows, not implementation)
- **Backend unit tests**: `./gradlew test` — ensure no regressions from Thymeleaf controller additions

### Phase 2 (Expenditure Dashboard)
- Add unit test for `TransactionService.getExpenditureSummary()` mocking the repository
- Add `@WebMvcTest` for the new endpoint
- Add `@DataJpaTest` for the native query with test data in various periods
- Flyway migration test: verify `weeklyLimit`/`monthlyLimit` columns added to `user_preferences`
- Manual verification: `make run-demo` → check dashboard period cards with seeded data
- Test edge cases: no transactions, first day of week/month, transactions only in some periods, limit exceeded vs. under-limit display
