# Budget Tracker Project Execution Plan

## Tech Stack Overview
* **Backend:** Spring Boot (Java 21+)
* **Frontend:** React (Vite, TypeScript, TailwindCSS)
* **Database:** PostgreSQL
* **Primary Keys:** UUIDv7 for all entities to prevent B-Tree fragmentation while ensuring distributed generation.

## Architecture Notes for Multi-User Extensibility
* Even though there is only one user initially, **all** domain entities (`Account`, `Category`, `Transaction`, `Label`) MUST include a `user_id` foreign key.
* All backend repository queries MUST scope data retrieval by the authenticated user's ID (e.g., `findByUserIdAndAccountId(...)`).
* Implement Spring Security with JWT from the start to establish the `UserPrincipal` context.
* **Testing:** Ensure that at each step, we keep adding unit tests, integration tests, or end-to-end tests and make sure that previous tests also pass with each feature implementation.

---

## Phase 1: Database Schema & Entity Modeling

### 1. Enums
* `AccountType`: `CREDIT_CARD`, `CASH`, `BANK_SAVINGS`, `CURRENT_ACCOUNT`
* `TransactionType`: `INCOME`, `EXPENSE`, `TRANSFER`

### 2. Core Tables (PostgreSQL)
* **`users`**: `id` (UUIDv7), `email`, `password_hash`, `created_at`
* **`accounts`**: `id`, `user_id`, `name` (e.g., "Chase Sapphire"), `type` (AccountType enum), `balance` (Decimal), `created_at`
* **`labels`**: `id`, `user_id`, `name` (e.g., NEEDS, WANTS, SAVINGS), `is_default` (Boolean) - Allows users to have custom labels while keeping system defaults.
* **`categories`**: `id`, `user_id`, `name` (e.g., Food, Travel), `icon` (String/Text for UI icon reference), `is_default` (Boolean - for system defaults) - 'Transfer' can be one of the default categories.
* **`transactions`**:
    * `id`, `user_id`, `account_id`
    * `category_id` (FK to categories)
    * `label_id` (FK to labels, Nullable)
    * `type` (TransactionType)
    * `amount` (Decimal)
    * `description` (Text)
    * `transaction_date` (Timestamp)
    * `linked_transfer_id` (UUID - Self-referencing FK used ONLY for transfers to link the outgoing and incoming transaction records)

*Note on PostgreSQL 13+ vs UUIDv7:* While PostgreSQL supports standard UUIDs natively, UUIDv7 (time-ordered) isn't a built-in function until potentially Postgres 17+. We will use a library like `uuid-creator` in Java or a custom Postgres extension/function if we want to generate them at the DB level. For simplicity, we'll generate them in the application layer (Spring Boot).

---

## Phase 2: Backend Development (Spring Boot)

### Step 1: Base Configuration
* Initialize Spring Boot project with Data JPA, PostgreSQL, Security, Web, and Validation.
* Add `uuid-creator` dependency for UUIDv7 generation in the application layer.
* Create a `@MappedSuperclass BaseEntity` that automatically generates a UUIDv7 `@PrePersist` if the ID is null.
* **Testing:** Add setup for Testcontainers with PostgreSQL for integration testing.

### Step 2: Domain Entities & Repositories
* Create JPA Entities (`User`, `Account`, `Category`, `Label`, `Transaction`) extending `BaseEntity`.
* Create Spring Data Repositories for each. Ensure all queries filter by `userId`.
* **Testing:** Write DataJpaTests to verify repository constraints and queries.

### Step 3: Core Services & Business Logic
* **AccountService**:
    * CRUD operations for accounts.
    * Method to calculate current balance based on transaction history (or update a cached balance field).
* **Category & Label Service**:
    * CRUD for categories and labels, initializing defaults for new users.
* **TransactionService**:
    * CRUD for standard incomes and expenses.
    * **Transfer Logic (`@Transactional`)**:
        * Validate both `from_account` and `to_account` belong to the user.
        * Create an `EXPENSE` transaction for the source account with the 'Transfer' category.
        * Create an `INCOME` transaction for the destination account with the 'Transfer' category.
        * Link both transactions via the `linked_transfer_id`.
        * Adjust balances for both accounts.
* **Testing:** Write extensive unit tests using Mockito for Services, especially the `@Transactional` transfer logic. Ensure validations are covered.

### Step 4: REST Controllers & Security
* Implement JWT Authentication filter.
* Expose REST endpoints under `/api/v1/`:
    * `POST /auth/register`, `POST /auth/login`
    * `GET/POST/PUT/DELETE /accounts`
    * `GET/POST/PUT/DELETE /labels`
    * `GET/POST/PUT/DELETE /categories`
    * `GET/POST/PUT/DELETE /transactions`
    * `POST /transactions/transfer` (Specific endpoint for account-to-account transfers)
* **Testing:** Write WebMvcTests / MockMvc integration tests for controllers ensuring authorization works and inputs are validated.

---

## Phase 3: Frontend Development (React)

### Step 1: Project Setup & Global State
* Scaffold Vite React TypeScript project.
* Setup Tailwind CSS for styling.
* Configure Axios with interceptors to attach the JWT token to all requests.
* Set up React Router for navigation.
* Create an Auth Context to manage the user session.
* **Testing:** Setup Vitest and React Testing Library.

### Step 2: Core Data Management UI (Accounts, Categories, Labels)
* **Accounts Dashboard**: Fetch and display a list of all accounts grouped by `AccountType`. Show current balances.
* **Settings/Management**: UI to manage custom Categories (with icons) and custom Labels.
* **Account Form Modal**: Form to create/edit an account (fields: Name, Type, Starting Balance).

### Step 3: Transaction Management UI
* **Transaction List**: A paginated or infinitely scrolling list of transactions.
    * Include visual indicators for Labels (e.g., custom colored badges fetched from label entity or assigned by UI).
    * Show Category icons.
* **Add Transaction Form**:
    * Standard transaction: Select Account, Type (Income/Expense), Category, Label, Amount, Date.
* **Transfer Funds Form**:
    * Custom UI specifically for transfers: Select "From Account", "To Account", Amount, Date, and Description. (Category defaults to 'Transfer').

### Step 4: Analytics & Insights (Optional but recommended)
* Build a pie chart component showing spending breakdown by Label (Needs vs. Wants vs. Savings) to track the 50/30/20 rule.
* Build a breakdown by Category.

---

## Phase 4: Finalization & Deployment Prep

* **Testing Review:** Run the full suite of unit, integration, and E2E tests (if applicable using Cypress/Playwright). Ensure all edge cases are covered.
* Configure CORS policies in Spring Boot.
* Set up environment variables for database credentials and JWT secrets.
