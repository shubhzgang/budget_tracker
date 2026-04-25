# Project Progress & TODO

This file tracks the progress of the Budget Tracker project against the original execution plan.

## ✅ Accomplished

### Project Infrastructure & Setup
- [x] Initialized Spring Boot project with Gradle, PostgreSQL, and Docker Compose.
- [x] Configured database schema via Flyway migration (`V1__initial_schema.sql`).
- [x] Resolved PostgreSQL credential mismatch between Docker and Spring Boot configurations.
- [x] Configured dependency management (fixed `uuid-creator` version and added `H2` for testing).
- [x] Set up a smoke test (`ApplicationContextTest`) using an in-memory H2 database.
- [ ] Set up Testcontainers with PostgreSQL for integration testing.

### Core Backend Foundation
- [x] Implemented `BaseEntity` with automated UUIDv7 generation.

---

## 🏗️ In Progress

### Backend Development (Phase 2)
- [ ] **Domain Entities & Repositories**:
    - [x] Implement JPA entity `User`.
    - [x] Implement JPA entity `Account` (include `type` enum: `CREDIT_CARD`, `CASH`, `BANK_SAVINGS`, `CURRENT_ACCOUNT`, `FRIEND_LENDING` and `credit_limit`).
    - [x] Implement JPA entity `Category` (include `icon`, `is_default`).
    - [x] Implement JPA entity `Label` (include `is_default`).
    - [x] Implement JPA entity `Transaction` (include `type` enum: `INCOME`, `EXPENSE`, `TRANSFER`, `LEND`, `BORROW`).
    - [x] Create Spring Data Repositories for all entities (scoping by `userId`).
    - [ ] Write `DataJpaTest` cases for all repositories verifying `userId` constraints.
- [ ] **Core Services**:
    - [x] Implement `AccountService` (CRUD, calculate balance, handle `credit_limit` and available credit).
    - [ ] Write unit tests for `AccountService`.
    - [ ] Implement `CategoryService` (CRUD, initializing defaults for new users).
    - [ ] Write unit tests for `CategoryService`.
    - [ ] Implement `LabelService` (CRUD, initializing defaults for new users).
    - [ ] Write unit tests for `LabelService`.
    - [ ] Implement `TransactionService` (CRUD for incomes/expenses).
    - [ ] Write unit tests for `TransactionService` (CRUD).
    - [ ] Implement `TransactionService` transfer logic (`@Transactional`, linking `linked_transfer_id`, adjusting balances, handling lending/borrowing).
    - [ ] Write unit tests for `TransactionService` transfer logic ensuring atomicity.
- [ ] **Security**:
    - [ ] Implement Spring Security with JWT-based authentication.
    - [ ] Establish `UserPrincipal` context.
    - [ ] Write tests for Security configuration and JWT filter.
- [ ] **REST API (Controllers)**:
    - [ ] `POST /auth/register`, `POST /auth/login` (including `WebMvcTest`)
    - [ ] `GET/POST/PUT/DELETE /accounts` (including `WebMvcTest`)
    - [ ] `GET/POST/PUT/DELETE /labels` (including `WebMvcTest`)
    - [ ] `GET/POST/PUT/DELETE /categories` (including `WebMvcTest`)
    - [ ] `GET/POST/PUT/DELETE /transactions` (including `WebMvcTest`)
    - [ ] `POST /transactions/transfer` (including `WebMvcTest` with validation checks)

### Frontend Development (Phase 3)
- [ ] **Project Setup**:
    - [ ] Scaffold Vite + React + TypeScript + TailwindCSS project.
    - [ ] Setup Vitest and React Testing Library.
    - [ ] Configure Axios with interceptors to attach JWT token.
    - [ ] Set up React Router for navigation.
    - [ ] Create Auth Context to manage user session.
- [ ] **Theming**:
    - [ ] Set up CSS variables and Tailwind themes for extensible UI theming.
    - [ ] Build abstraction around CSS variables (colors, typography, borders, shadows) in `tailwind.config.js`.
    - [ ] Implement `ThemeContext` in React.
    - [ ] Ensure out-of-the-box support for "Light" and "Dark" themes.
- [ ] **Core Data Management UI**:
    - [ ] **Accounts Dashboard**: Fetch and display accounts grouped by type, show balances.
        - [ ] Show progress bar for credit utilization on `CREDIT_CARD` accounts.
        - [ ] Show "They owe you / You owe them" for `FRIEND_LENDING` accounts.
    - [ ] **Settings/Management**: UI to manage custom Categories (with icons), custom Labels, and Theme switching.
    - [ ] **Account Form Modal**: Create/edit account (conditionally show "Credit Limit" if `CREDIT_CARD`).
    - [ ] Write component tests using React Testing Library for Dashboards and Modals.
- [ ] **Transaction Management UI**:
    - [ ] **Transaction List**: Paginated/infinite scrolling list of transactions (show Label badges, Category icons).
    - [ ] **Add Transaction Form**: Select Account, Type, Category, Label, Amount, Date.
    - [ ] **Transfer Funds Form**: Select From/To Account, Amount, Date, Description (used for regular transfers and lending/borrowing).
    - [ ] Write component tests for Transaction List and Forms.
- [ ] **Analytics & Insights (Optional)**:
    - [ ] Pie chart for spending breakdown by Label (Needs vs. Wants vs. Savings).
    - [ ] Breakdown by Category.

### Finalization & Deployment (Phase 4)
- [ ] **Testing Review**: Run full suite of unit, integration, and E2E tests.
- [ ] **Configuration**: Configure CORS policies in Spring Boot and environment variables.
- [ ] **Dockerization**:
    - [ ] Create multi-stage `Dockerfile` for Backend.
    - [ ] Create multi-stage `Dockerfile` for Frontend.
- [ ] **Orchestration**: Finalize `docker-compose.yml` for unified stack deployment (Postgres, Backend, Frontend).
