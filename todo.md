# Project Progress & TODO

This file tracks the progress of the Budget Tracker project against the original execution plan.

## ✅ Accomplished

### Project Infrastructure & Setup
- [x] Initialized Spring Boot project with Gradle, PostgreSQL, and Docker Compose.
- [x] Configured database schema via Flyway migration (`V1__initial_schema.sql`).
- [x] Resolved PostgreSQL credential mismatch between Docker and Spring Boot configurations.
- [x] Configured dependency management (fixed `uuid-creator` version and added `H2` for testing).
- [x] Fixed Lombok version to `1.18.38` for Java 26 compatibility.
- [x] Set up a smoke test (`ApplicationContextTest`) using an in-memory H2 database.
- [x] Set up Testcontainers with PostgreSQL for integration testing.
- [x] Excluded integration tests (tagged `@Tag("integration")`) from `gradle test` — they run only via `make test-int` with Docker containers.

### Core Backend Foundation
- [x] Implemented `BaseEntity` with automated UUIDv7 generation.

---

## 🏗️ In Progress

### Backend Development (Phase 2)
- [x] **Domain Entities & Repositories**:
    - [x] Implement JPA entity `User`.
    - [x] Implement JPA entity `Account` (include `type` enum: `CREDIT_CARD`, `CASH`, `BANK`, `FRIEND_LENDING` and `credit_limit`).
    - [x] Implement JPA entity `Category` (include `icon`, `is_default`).
    - [x] Implement JPA entity `Label` (include `is_default`).
    - [x] Implement JPA entity `Transaction` (include `type` enum: `INCOME`, `EXPENSE`, `TRANSFER`, `LEND`, `BORROW` and `to_account_id`).
    - [x] Create Spring Data Repositories for all entities (scoping by `userId`).
    - [x] Write `DataJpaTest` cases for all repositories verifying `userId` constraints.
- [x] **Core Services**:
    - [x] Implement `AccountService` (CRUD, calculate balance, handle `credit_limit` and available credit).
    - [x] Write unit tests for `AccountService`.
    - [x] Implement `CategoryService` (CRUD, initializing defaults for new users).
    - [x] Write unit tests for `CategoryService`.
    - [x] Implement `LabelService` (CRUD, initializing defaults for new users).
    - [x] Write unit tests for `LabelService`.
    - [x] Implement `TransactionService` (CRUD for incomes/expenses).
    - [x] Write unit tests for `TransactionService` (CRUD).
    - [x] Implement `TransactionService` transfer logic (`@Transactional`, using `to_account_id`, adjusting balances, handling lending/borrowing).
    - [x] Write unit tests for `TransactionService` transfer logic ensuring atomicity.
- [x] **Security**:
    - [x] Implement Spring Security with JWT-based authentication.
    - [x] Establish `UserPrincipal` context.
    - [x] Write tests for Security configuration and JWT filter.
- [x] **REST API (Controllers)**:
    - [x] `POST /auth/register`, `POST /auth/login` (including `WebMvcTest`)
    - [x] `GET/POST/PUT/DELETE /accounts` (including `WebMvcTest`)
    - [x] `GET/POST/PUT/DELETE /labels` (including `WebMvcTest`)
    - [x] `GET/POST/PUT/DELETE /categories` (including `WebMvcTest`)
    - [x] `GET/POST/PUT/DELETE /transactions` (including `WebMvcTest`)
    - [x] `POST /transactions/transfer` (including `WebMvcTest` with validation checks)
- [x] **Verification**:
    - [x] Implement full black-box integration tests for all resources.
    - [x] Verify PostgreSQL enum compatibility in Docker environment.
    - [x] Fix circular references in Transaction linking logic.
    - [x] Fix transfer balance bug (Cash/Bank directionality) and UI visual metadata.

### Frontend Development (Phase 3)
- [x] **Project Setup**:
    - [x] Scaffold Vite + React + TypeScript + TailwindCSS project.
    - [x] Setup Vitest and React Testing Library.
    - [x] Configure Axios with interceptors to attach JWT token.
    - [x] Set up React Router for navigation.
    - [x] Create Auth Context to manage user session.
- [x] **Theming**:
    - [x] Set up CSS variables and Tailwind themes for extensible UI theming.
    - [x] Build abstraction around CSS variables (colors, typography, borders, shadows) in `tailwind.config.js`.
    - [x] Implement `ThemeContext` in React.
    - [x] Ensure out-of-the-box support for "Light" and "Dark" themes.
- [x] **Core Data Management UI**:
    - [x] **Accounts Dashboard**: Fetch and display accounts grouped by type, show balances.
        - [x] Show progress bar for credit utilization on `CREDIT_CARD` accounts.
        - [x] Show "They owe you / You owe them" for `FRIEND_LENDING` accounts.
    - [x] **Settings/Management**: UI to manage custom Categories (with icons), custom Labels, and Theme switching.
    - [x] **Account Form Modal**: Create/edit account (conditionally show "Credit Limit" if `CREDIT_CARD`).
    - [x] Write component tests using React Testing Library for Dashboards and Modals.
- [x] **Transaction Management UI**:
    - [x] **Transaction List**: Paginated/infinite scrolling list of transactions (show Label badges, Category icons).
    - [x] **Add Transaction Form**: Select Account, Type, Category, Label, Amount, Date.
    - [x] **Transfer Funds Form**: Select From/To Account, Amount, Date, Description (used for regular transfers and lending/borrowing).
    - [x] Write component tests for Transaction List and Forms.
- [x] **Analytics & Insights (Optional)**:
    - [x] Pie chart for spending breakdown by Label (Needs vs. Wants vs. Savings).
    - [x] Breakdown by Category.
- [x] **Currency Customization**:
    - [x] Add `currencySymbol` to `UserPreference` (default ₹).
    - [x] Update `PreferenceManager` UI to allow choosing symbol.
    - [x] Update formatting across the app to use preferred symbol.
- [ ] **Toast Notifications**:
    - [ ] Implement toast notifications for successful actions (e.g., "Account created", "Transaction saved").

### 🐛 Pending Bug Fixes
- [ ] **Transaction Search**: Search on transactions page not working for `TRANSFER` type transactions (searching by description).
- [x] **SQL Restore**: Backup failed to restore after "Delete All Data" operation was performed. (Fixed: improved SQL split logic and added UTF-8 support in `BackupService`).
- [ ] **"Transfer to undefined" in UI**: Transfer transactions display "Transfer to undefined" instead of the destination account name. Root cause: `Transaction` entity is missing `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})`, so the lazy-loaded `toAccount` serializes as `null` in JSON. Fix: add the annotation to `Transaction.java` (like `Account.java` already has).

### 🚀 Future Enhancements
- [ ] **In-Place Restore**: Ability to restore directly from the server-side backup history in the UI without downloading/uploading.
- [ ] When I search with type income on all transactions page, I want to see these transfer transactions also because the amount is being added to an account. Also, add
  filter to search transactions by account which also returns these kind of incoming transfers. On the UI page, I want to see the description instead of the generic    
  "Transfer to account" text. For transfer types, in the UI show arrow from one account to another where just the main account is shown. Read @CLAUDE_CONTEXT.md file   
  for basic understanding of project.

### Finalization & Deployment (Phase 4)
- [ ] **Testing Review**: Run full suite of unit, integration, and E2E tests.
- [ ] **Configuration**: Configure CORS policies in Spring Boot and environment variables.
- [x] **Dockerization**:
    - [x] Create multi-stage `Dockerfile` for Backend.
    - [x] Create multi-stage `Dockerfile` for Frontend.
- [x] **Orchestration**: Finalize `docker-compose.yml` for unified stack deployment (Postgres, Backend, Frontend).
