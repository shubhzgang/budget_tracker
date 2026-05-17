# Project Context: Budget Tracker

## Overview
Budget Tracker is a full-stack application for managing personal finances, featuring accounts, categories, labels, and transactions with support for different account types (e.g., Credit Cards, Bank, Friend Lending).

## Tech Stack
- **Backend**: Java 21, Spring Boot, Spring Security (JWT), Spring Data JPA, PostgreSQL, Gradle.
- **Frontend**: React (Vite), TypeScript, Tailwind CSS, Vitest, React Testing Library.
- **Testing**: JUnit 5, Playwright (E2E), Testcontainers (Integration), Vitest (Frontend).
- **Infrastructure**: Docker, Docker Compose, Nginx (for frontend serving).

## Architecture & Design
- **Identity**: Multi-user architecture. All entities (`Account`, `Category`, `Label`, `Transaction`) are scoped to a `user_id`.
- **Primary Keys**: Uses UUIDv7 for all entities to ensure time-ordered, distributed ID generation and prevent B-Tree fragmentation.
- **Security**: JWT-based authentication with a `UserPrincipal` context.
- **Transactions**: Supports Income, Expense, Transfer, Lend, and Borrow. Transfers (including lending/borrowing) use a `to_account_id` to maintain atomicity and auditability within a single record.
- **Theming**: Extensible CSS variable-based theming (Light/Dark) using Tailwind CSS.

## Core Components
- **Backend (`/src`)**: 
    - `controller`: REST endpoints for all domain entities.
    - `service`: Business logic (especially complex transfer/balance logic).
    - `repository`: Spring Data JPA repositories.
    - `security`: JWT filters and security configurations.
    - `model`: JPA entities.
- **Frontend (`/frontend`)**:
    - `src/components`: Reusable UI components (Modals, Cards, Forms).
    able to handle dynamic forms based on transaction type.
    - `src/context`: Auth, Theme, and UI state management.
    - `src/pages`: Main application views (Dashboard, Transactions, Settings).
- **E2E (`/e2e`)**: Playwright test suites for critical user flows.

## Key Commands
- **Backend Unit/Integration Tests**: `./gradlew test`
- **Backend Integration Tests (with Docker)**: `make test-int`
- **Frontend Tests**: `cd frontend && npm test`
- **End-to-End Tests (Full Stack)**: `make test-e2e`
- **Run Entire Stack**: `make run-stack` (via Docker Compose)
- **Run Demo Mode**: `make run-demo`
