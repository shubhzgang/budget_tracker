# Frontend Rewrite & Expenditure Dashboard Feature Plan

## Current Architecture Summary

| Layer | Technology | Notes |
|---|---|---|
| Backend | Java 21, Spring Boot 3.5, JPA, PostgreSQL 18, Flyway | Well-structured, proper pagination via Spring Data `Page` |
| Frontend | React 19 + Vite 8 + TypeScript 6 + Tailwind 4 + Recharts 3 | **Heavyweight for the use case** |
| Auth | JWT (Bearer token in localStorage) | Standard |
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

#### Auth: Dual SecurityFilterChain (Session + JWT)

The current `SecurityConfig.java` uses `SessionCreationPolicy.STATELESS` and disables CSRF. We need to support **both** auth mechanisms simultaneously during migration (and permanently for MCP access). This requires **two `SecurityFilterChain` beans** with `@Order`:

```java
// API chain — stateless JWT (existing behavior, higher priority)
@Bean
@Order(1)
public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher("/api/v1/**")
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/**").permitAll()
            .anyRequest().authenticated()
        )
        .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(authContextFilter, AuthTokenFilter.class);
    return http.build();
}

// Web chain — session-based for Thymeleaf pages (lower priority, catches everything else)
@Bean
@Order(2)
public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher("/**")
        .csrf(Customizer.withDefaults())  // CSRF enabled
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/login", "/css/**", "/js/**", "/vendor/**", "/actuator/health").permitAll()
            .anyRequest().authenticated()
        )
        .formLogin(form -> form
            .loginPage("/login")
            .defaultSuccessUrl("/dashboard", true)
        )
        .logout(logout -> logout.logoutSuccessUrl("/login"));
    return http.build();
}
```

Key points:
- `/api/v1/**` matched first (`@Order(1)`) — stateless JWT, CSRF disabled, existing behavior unchanged
- `/**` matched second (`@Order(2)`) — session-based, CSRF enabled, form login
- The `AuthContext` ThreadLocal pattern still works — populated from session `UserDetails` via a filter on the web chain

#### CSRF Protection for HTMX

HTMX does not automatically send CSRF tokens. The solution:

1. **Inject the token via Thymeleaf** in `layout.html`:
```html
<meta name="_csrf" th:content="${_csrf.token}">
<meta name="_csrf_header" th:content="${_csrf.headerName}">
```

2. **Configure HTMX** to attach it to every mutating request:
```html
<script>
  document.addEventListener('htmx:configRequest', function(event) {
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    if (csrfToken && csrfHeader) {
      event.detail.headers[csrfHeader] = csrfToken;
    }
  });
</script>
```

This ensures all `hx-post`, `hx-put`, `hx-delete` requests include the CSRF token header automatically.

#### New Thymeleaf Controllers

Create a new set of controllers that return Thymeleaf views/fragments instead of JSON. These live alongside the existing API controllers:

| Route | Purpose | Returns |
|---|---|---|
| `GET /login` | Login page | Full page |
| `POST /login` | Form login | Redirect to `/dashboard` |
| `GET /dashboard` | Dashboard page | Full page |
| `GET /dashboard/accounts` | Accounts section fragment | HTML fragment |
| `GET /dashboard/recent` | Recent transactions fragment | HTML fragment |
| `GET /transactions` | Transactions page | Full page |
| `GET /transactions/list` | Transaction list fragment (paginated) | HTML fragment |
| `GET /accounts/form` | Account create/edit form | HTML fragment (for modal) |
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

### Migration Approach

1. **Keep both frontends running** during migration — the React frontend at its current path, HTMX frontend served by Spring Boot directly
2. Migrate page by page: Login → Dashboard → Transactions → Settings
3. Once all pages are migrated and verified, remove the `frontend/` directory and its Docker container
4. Update `docker-compose.yml` to remove the frontend service (Spring Boot serves everything)
5. Update `Dockerfile` to include static assets in the Spring Boot jar

### What Gets Deleted After Migration

```
frontend/                    # Entire React frontend directory (~197KB source + node_modules)
├── src/                     # All React components, contexts, pages, types
├── package.json             # React, Vite, Tailwind, Recharts dependencies
├── vite.config.ts
├── tailwind.config.js
├── tsconfig*.json
├── Dockerfile               # Separate frontend Docker image
└── ...
```

The `docker-compose.yml` frontend service also gets removed. Spring Boot serves the Thymeleaf HTML directly.

---

## Phase 2: Expenditure Period Feature (on HTMX stack)

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

#### Files to create/modify:

| File | Change |
|---|---|
| `ExpenditureSummaryResponse.java` | **[NEW]** DTO with 6 `BigDecimal` fields |
| `TransactionRepository.java` | **[MODIFY]** Add `@Query(nativeQuery=true)` method |
| `TransactionService.java` | **[MODIFY]** Add `getExpenditureSummary()` method |
| `TransactionController.java` | **[MODIFY]** Add `GET /expenditure-summary` endpoint |

### Frontend — Dashboard Period Cards (Thymeleaf + HTMX)

A row of clickable period cards rendered as a Thymeleaf fragment:

```
┌─────────────┐ ┌─────────────┐ ┌──────────────┐ ┌─────────────┐ ┌──────────────┐ ┌─────────────┐
│  Yesterday  │ │   Today     │ │  Last Week   │ │ This Week   │ │  Last Month  │ │ This Month  │
│  ₹1,250.00  │ │  ₹430.00    │ │  ₹8,750.00   │ │ ₹3,200.00   │ │  ₹42,500.00  │ │ ₹15,800.00  │
└─────────────┘ └─────────────┘ └──────────────┘ └─────────────┘ └──────────────┘ └─────────────┘
                                                    ▲ active
```

Clicking a card uses `hx-get` to re-fetch the analytics fragment with `startDate`/`endDate` query params, updating the charts below.

#### Files to create/modify:

| File | Change |
|---|---|
| `fragments/period-cards.html` | **[NEW]** Period cards Thymeleaf fragment |
| `fragments/analytics.html` | **[MODIFY]** Accept date range params, filter chart data |
| `dashboard.html` | **[MODIFY]** Include period cards section |
| `DashboardViewController.java` | **[MODIFY]** Fetch expenditure summary, pass to template |

---

## Verification Plan

### Phase 1 (HTMX Rewrite)
- **Per-page manual verification**: After migrating each page, visually compare with the React version
- **Playwright E2E tests**: Run `make test-e2e` — existing E2E tests should pass against the new frontend (they test user flows, not implementation)
- **Backend unit tests**: `./gradlew test` — ensure no regressions from Thymeleaf controller additions

### Phase 2 (Expenditure Feature)
- Add unit test for `TransactionService.getExpenditureSummary()` mocking the repository
- Add `@WebMvcTest` for the new endpoint
- Manual verification: `make run-demo` → check dashboard period cards with seeded data
- Test edge cases: no transactions, first day of week/month, transactions only in some periods
