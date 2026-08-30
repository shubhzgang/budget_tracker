# Project Context: Budget Tracker

## Overview
Budget Tracker is a full-stack application for managing personal finances, featuring accounts, categories, labels, and transactions with support for different account types (e.g., Credit Cards, Bank, Friend Lending).

## Tech Stack
- **Backend**: Java 21, Spring Boot, Spring Security (JWT), Spring Data JPA, PostgreSQL, Gradle. Schema migrations via Flyway (`V1__initial_schema.sql`, `V2__expenditure_period_totals.sql`).
- **Frontend**: HTMX + Thymeleaf server-rendered templates with Alpine.js for interactivity and plain CSS (CSS-variable theming: Light/Dark/OLED). The old React/Vite frontend has been fully removed (see `frontend-rewrite-plan.md`).
- **Testing**: JUnit 5, Playwright (E2E), Testcontainers (Integration, via `make test-int`), contract tests in `src/test/java/com/budget/tracker/contract`.
- **Infrastructure**: Docker, Docker Compose (Postgres + Backend only; Spring Boot serves the UI and static assets).

## Architecture & Design
- **Identity**: Multi-user architecture. All entities (`Account`, `Category`, `Label`, `Transaction`, `Transfer`) are scoped to a `user_id`.
- **Primary Keys**: Uses UUIDv7 for all entities to ensure time-ordered, distributed ID generation and prevent B-Tree fragmentation.
- **Security**: JWT stored in a secure HttpOnly cookie (set on login alongside the JSON response); `Authorization: Bearer` header also supported. Stateless filter chain; CSRF disabled with a bespoke `CsrfHeaderFilter` HTMX/Bearer guard. Registration can be toggled via `app.auth.register-enabled`.
- **Transactions vs Transfers**: Split into separate tables. `transactions` holds INCOME/EXPENSE/LEND/BORROW; `transfers` is a dedicated table with `fromAmount`, `toAmount`, and `adjustment` (discount/savings) — any two fields auto-compute the third. A PostgreSQL `activity_view` (`UNION ALL`) provides unified listing/search.
- **Expenditure Dashboard**: Hybrid computation — Today/Yesterday totals computed live; Week/Month totals eagerly maintained in `expenditure_period_totals` via atomic `ON CONFLICT DO UPDATE` upserts on every transaction write. Timezone pinned to `TimeZones.APP_ZONE` (Asia/Kolkata).
- **Theming**: Cookie-based theme (`data-theme` rendered server-side, no FOUC) with CSS variables in `static/css/style.css`.

## Core Components
- **Backend (`/src`)**:
    - `controller`: REST API endpoints under `/api/v1` (auth, accounts, categories, labels, transactions, transfers, backups, expenditure-summary).
    - `web`: Thymeleaf page/fragment controllers (`DashboardViewController`, `TransactionsViewController`, `SettingsViewController`, `BackupsViewController`, `AuthPagesController`) + `PageContextInterceptor` (theme, currency formatter).
    - `service`: Business logic (transfer/balance math, expenditure summary upserts, backups).
    - `repository`: Spring Data JPA repositories (scoped by `userId`).
    - `security`: JWT filter (`AuthTokenFilter`), `SecurityConfig`, `UserPrincipal`.
    - `model`: JPA entities.
- **Templates (`src/main/resources/templates`)**: `layout.html`, `login.html`, `register.html`, `dashboard.html`, `transactions.html`, `settings.html` + `fragments/` (account/activity/transaction forms, period-cards, category/label managers, backup-manager, emoji-picker, etc.).
- **E2E (`/e2e`)**: Playwright test suites for critical user flows.

## Key Commands
- **Backend Unit Tests**: `./gradlew test`
- **Backend Integration Tests (Docker)**: `make test-int`
- **End-to-End Tests (Full Stack)**: `make test-e2e`
- **Run Entire Stack**: `make run-stack` (via Docker Compose, app on host port 3300)
- **Run Demo Mode**: `make run-demo`

## Current State & Next Steps
- ✅ HTMX + Thymeleaf rewrite complete; React frontend removed; spending-insights section removed from dashboard.
- ▶️ **Next up**: Implement label-wise expenditure breakdown on dashboard period cards — full plan in `label-wise-expenditure-breakdown-plan.md` (V3 migration adding `label_name` to `expenditure_period_totals`, per-label upserts, `*ByLabel` fields in `ExpenditureSummaryResponse`, `period-cards.html` UI). Not yet implemented.
- Backlog (`todo.md`): CSV export by time range, in-place restore from server-side backups, account flag to exclude from budget/net-worth.
- Open review concerns (`htmx-rewrite-review-concerns.md`): expired-session handling for htmx requests, CSRF posture, token/CORS hygiene, hardcoded Asia/Kolkata, vendored JS, test coverage gaps.

## Known / Intentional Behaviors
- **CSV import does NOT update account balances.** `BackupService.importFromCsv` saves the imported transactions (and auto-creates accounts/categories/labels as needed) but never applies the transaction amount to `account.balance` — it only calls `recomputeForUser()` at the end, which rebuilds the *expenditure period totals* (dashboard), not account balances. Auto-created accounts are left at `₹0` and existing accounts retain their pre-import balance regardless of the imported rows. **This is accepted/expected (2026-08-30, user decision)** — CSV is treated as a transaction-history port, not a balance restore. Do not "fix" this without explicit approval.
