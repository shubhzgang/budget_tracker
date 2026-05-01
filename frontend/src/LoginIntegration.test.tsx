import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import { AppRoutes } from './App';

describe('Login Integration', () => {
  it('allows a user to log in and redirects to dashboard', async () => {
    render(
      <ThemeProvider>
        <AuthProvider>
          <MemoryRouter initialEntries={['/login']}>
            <AppRoutes />
          </MemoryRouter>
        </AuthProvider>
      </ThemeProvider>
    );

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
    render(
      <ThemeProvider>
        <AuthProvider>
          <MemoryRouter initialEntries={['/login']}>
            <AppRoutes />
          </MemoryRouter>
        </AuthProvider>
      </ThemeProvider>
    );

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
});
