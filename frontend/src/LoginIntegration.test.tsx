import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { PreferenceProvider } from './context/PreferenceContext';
import { ThemeProvider } from './context/ThemeContext';
import { UIProvider } from './context/UIContext';
import { ToastProvider } from './context/ToastContext';
import { AppRoutes } from './App';

const renderApp = (initialPath = '/login') =>
  render(
    <ThemeProvider>
      <AuthProvider>
        <PreferenceProvider>
          <ToastProvider>
            <UIProvider>
              <MemoryRouter initialEntries={[initialPath]}>
                <AppRoutes />
              </MemoryRouter>
            </UIProvider>
          </ToastProvider>
        </PreferenceProvider>
      </AuthProvider>
    </ThemeProvider>
  );

describe('Login Integration', () => {
  afterEach(() => {
    sessionStorage.clear();
  });

  it('allows a user to log in and redirects to dashboard', async () => {
    renderApp();

    // Initially on Login page
    expect(screen.getByRole('heading', { name: /Login/i })).toBeInTheDocument();

    // Fill out the form
    const emailInput = screen.getByPlaceholderText(/Email/i);
    const passwordInput = screen.getByPlaceholderText(/Password/i);
    const signInButton = screen.getByRole('button', { name: /Sign In/i });

    fireEvent.change(emailInput, { target: { value: 'test@example.com' } });
    fireEvent.change(passwordInput, { target: { value: 'password123' } });
    fireEvent.click(signInButton);

    // Should redirect to Dashboard (which shows "Accounts" heading)
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /Accounts/i })).toBeInTheDocument();
    }, { timeout: 4000 });

    // Verify localStorage was updated
    expect(localStorage.setItem).toHaveBeenCalledWith('token', 'mock-jwt-token');
  });

  it('displays an error message when login fails', async () => {
    renderApp();

    const emailInput = screen.getByPlaceholderText(/Email/i);
    const passwordInput = screen.getByPlaceholderText(/Password/i);
    const signInButton = screen.getByRole('button', { name: /Sign In/i });

    fireEvent.change(emailInput, { target: { value: 'error@example.com' } });
    fireEvent.change(passwordInput, { target: { value: 'wrong-password' } });
    fireEvent.click(signInButton);

    await waitFor(() => {
      expect(screen.getByText(/Invalid email or password/i)).toBeInTheDocument();
    });
  });

  it('shows a session expired notice when redirected after a session expiry', () => {
    sessionStorage.setItem('sessionExpired', 'true');
    renderApp();

    expect(
      screen.getByText(/Your session has expired. Please sign in again./i)
    ).toBeInTheDocument();
    expect(sessionStorage.getItem('sessionExpired')).toBeNull();
  });

  it('does not show the session expired notice on a normal visit', () => {
    renderApp();

    expect(
      screen.queryByText(/Your session has expired. Please sign in again./i)
    ).not.toBeInTheDocument();
  });
});
