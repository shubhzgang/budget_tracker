# Budget Tracker Project Execution Plan

## Tech Stack Overview
* **Backend:** Spring Boot (Java 21+)
* **Frontend:** React (Vite, TypeScript, TailwindCSS)
* **Database:** PostgreSQL
* **Primary Keys:** UUIDv7 for all entities to prevent B-Tree fragmentation while ensuring distributed generation.
* **Deployment:** Docker & Docker Compose
* **Build Tool:** Gradle

## Architecture Notes for Multi-User Extensibility
* Even though there is only one user initially, **all** domain entities (`Account`, `Category`, `Transaction`, `Label`) MUST include a `user_id` foreign key.
* All backend repository queries MUST scope data retrieval by the authenticated user's ID (e.g., `findByUserIdAndAccountId(...)`).
* Implement Spring Security with JWT from the start to establish the `UserPrincipal` context.
* **Testing:** Ensure that at each step, we keep adding unit tests, integration tests, or end-to-end tests and make sure that previous tests also pass with each feature implementation.

---

## Phase 1: Database Schema & Entity Modeling

### 1. Enums
* `AccountType`: `CREDIT_CARD`, `CASH`, `BANK`, `FRIEND_LENDING`
* `TransactionType`: `INCOME`, `EXPENSE`, `TRANSFER`, `LEND`, `BORROW`

### 2. Core Tables (PostgreSQL)
* **`users`**: `id` (UUIDv7), `email`, `password_hash`, `created_at`
* **`accounts`**: `id`, `user_id`, `name` (e.g., "Chase Sapphire", "Alice"), `type` (AccountType enum), `balance` (Decimal), `credit_limit` (Decimal, Nullable - used primarily for `CREDIT_CARD` type), `created_at`
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

*Note on Account Types:* 
*   **Credit Cards:** When creating an account of type `CREDIT_CARD`, a `credit_limit` can be specified. The UI will display "Remaining Credit" by calculating `credit_limit - balance`.
*   **Friends/Lending:** We are modeling friends as a specific `AccountType` (`FRIEND_LENDING`). Lending money to a friend is effectively a transfer from a cash/bank account to the "Friend" account. Borrowing money is a transfer from the "Friend" account to your cash/bank account. The `balance` on the "Friend" account represents the net amount they owe you (positive balance) or you owe them (negative balance).

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
    * Handle optional `credit_limit` during creation/updates.
    * Method to calculate current balance based on transaction history (or update a cached balance field). For credit cards, calculate `available_credit`.
* **Category & Label Service**:
    * CRUD for categories and labels, initializing defaults for new users.
* **TransactionService**:
    * CRUD for standard incomes and expenses.
    * **Transfer Logic (`@Transactional`)**:
        * Validate both `from_account` and `to_account` belong to the user.
        * Handle regular account-to-account transfers.
        * Handle LENDING/BORROWING logic (which fundamentally operates as a transfer to/from a `FRIEND_LENDING` account type).
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
    * `POST /transactions/transfer` (Specific endpoint for account-to-account transfers, including lending/borrowing)
* **Testing:** Write WebMvcTests / MockMvc integration tests for controllers ensuring authorization works and inputs are validated.

---

## Phase 3: Frontend Development (React)

### Step 1: Project Setup & Global State
* Scaffold Vite React TypeScript project.
* Setup Tailwind CSS for styling. Set up CSS variables and Tailwind themes to support extensible UI theming.
* **Theming Implementation**: Build an abstraction around CSS variables for colors, typography, borders, and shadows (e.g., using Tailwind's `theme` extension in `tailwind.config.js`). Provide a ThemeContext in React. Ensure out-of-the-box support for "Light" and "Dark" themes while keeping it structured so a user could later add themes like "OLED Dark" or "Frosted Glass".
* Configure Axios with interceptors to attach the JWT token to all requests.
* Set up React Router for navigation.
* Create an Auth Context to manage the user session.
* **Testing:** Setup Vitest and React Testing Library.

### Step 2: Core Data Management UI (Accounts, Categories, Labels)
* **Accounts Dashboard**: Fetch and display a list of all accounts grouped by `AccountType`. Show current balances.
    * *Credit Card Specifics:* For `CREDIT_CARD` types, display a progress bar indicating credit utilization based on `credit_limit` and current balance.
    * *Friend Accounts Specifics:* For `FRIEND_LENDING` types, display "They owe you X" (positive balance) or "You owe them X" (negative balance).
* **Settings/Management**: UI to manage custom Categories (with icons) and custom Labels. Also include Theme switching in Settings.
* **Account Form Modal**: Form to create/edit an account (fields: Name, Type, Starting Balance). Conditionally show "Credit Limit" if Type is `CREDIT_CARD`.

### Step 3: Transaction Management UI
* **Transaction List**: A paginated or infinitely scrolling list of transactions.
    * Include visual indicators for Labels (e.g., custom colored badges fetched from label entity or assigned by UI).
    * Show Category icons.
* **Add Transaction Form**:
    * Standard transaction: Select Account, Type (Income/Expense), Category, Label, Amount, Date.
* **Transfer Funds Form**:
    * Custom UI specifically for transfers: Select "From Account", "To Account", Amount, Date, and Description. (Category defaults to 'Transfer'). Use this interface for Lending/Borrowing with friend accounts as well.

### Step 4: Analytics & Insights (Optional but recommended)
* Build a pie chart component showing spending breakdown by Label (Needs vs. Wants vs. Savings) to track the 50/30/20 rule.
* Build a breakdown by Category.

---

## Phase 4: Finalization & Deployment Prep (Docker)

* **Testing Review:** Run the full suite of unit, integration, and E2E tests (if applicable using Cypress/Playwright). Ensure all edge cases are covered.
* Configure CORS policies in Spring Boot.
* Set up environment variables for database credentials and JWT secrets.

### Dockerization & Compose Setup
* **Backend Dockerfile:** Create a multi-stage `Dockerfile` for the Spring Boot application (using Maven/Gradle to build, and a lightweight JRE image for runtime).
* **Frontend Dockerfile:** Create a multi-stage `Dockerfile` for the React application (build with Node, serve with Nginx).
* **Docker Compose (`docker-compose.yml`):**
    * Create a unified `docker-compose.yml` file to orchestrate the entire stack.
    * Define services for:
        * `db`: PostgreSQL container (with a mapped volume for data persistence).
        * `backend`: Spring Boot API container (depends on `db`).
        * `frontend`: Nginx container serving React (depends on `backend`).
    * Utilize a `.env` file to feed environment variables to the containers (e.g., DB passwords, JWT secrets, API URLs).
    *   Ensure the backend waits for the database to be healthy before starting up using `depends_on` and `healthcheck` conditions.

    ---

    ## Phase 5: Refinement & Bug Fixes (Next Session)

    ### 1. Transfer Transaction Logic Fix
    * **Issue:** When creating a transfer, the UI hides the Category field, but the previously selected category (or the first one in the list) is still being sent and applied to the transaction.
    * **Fix:** 
    * Ensure the `TransactionForm` state resets or explicitly clears the `categoryId` and `labelId` when the transaction type is set to `TRANSFER`.
    * Transfers should be strictly account-to-account without category or label association unless intentionally required.

    ### 2. Currency Customization & Localization
    * **Default Change:** Update the default currency symbol from `$` to `₹`.
    * **User Preference:** 
    * Add a `currencySymbol` field to the `UserPreference` model/entity.
    * Update the `PreferenceManager` UI to allow users to choose their preferred currency symbol.
    * Ensure all currency displays throughout the app (Dashboard, Transactions, Analytics) use the user's preferred symbol.

    ### 3. Dashboard UI Refactor (Accounts Section)
    * **Consolidated List:** Remove separate sections for each account type on the dashboard. Since the type is already displayed within the account card, listing them together reduces vertical clutter.
    * **Responsive Layout:**
    * Refactor the accounts container to use a grid layout.
    * On mobile, maintain a single column.
    * On larger screens, use multiple columns (e.g., `grid-cols-2` or `grid-cols-3`) to utilize horizontal space effectively.
    * Ensure the "Add Account" button remains easily accessible or integrated into the new layout.