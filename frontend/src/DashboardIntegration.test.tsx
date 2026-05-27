import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { PreferenceProvider } from './context/PreferenceContext';
import { ThemeProvider } from './context/ThemeContext';
import { UIProvider } from './context/UIContext';
import { ToastProvider } from './context/ToastContext';
import { Dashboard } from './pages/Dashboard';

describe('Dashboard Integration', () => {
  it('fetches and displays accounts grouped by type', async () => {
    // Auth context needs a user/token to render Dashboard
    localStorage.setItem('token', 'mock-token');
    localStorage.setItem('user', JSON.stringify({ id: '1', email: 'test@example.com' }));

    render(
      <ThemeProvider>
        <AuthProvider>
          <PreferenceProvider>
            <ToastProvider>
              <UIProvider>
                <MemoryRouter>
                  <Dashboard />
                </MemoryRouter>
              </UIProvider>
            </ToastProvider>
          </PreferenceProvider>
        </AuthProvider>
      </ThemeProvider>
    );

    // Should show Net Worth (calculated from MSW mock in handlers.ts: 1000 + 50 = 1050)
    await waitFor(() => {
      expect(screen.getByText('₹1,050.00')).toBeInTheDocument();
    });

    // Should show account names (multiple occurrences due to transaction list)
    expect(screen.getAllByText('Main Bank')[0]).toBeInTheDocument();
    expect(screen.getByText('Cash')).toBeInTheDocument();

    // Should show group headers
    expect(screen.getAllByText(/BANK/i)[0]).toBeInTheDocument();
    expect(screen.getAllByText(/CASH/i)[0]).toBeInTheDocument();
    
    // Verify Section headings
    expect(screen.getByRole('heading', { name: /Accounts/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Spending Insights/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Recent Transactions/i })).toBeInTheDocument();

    // Should show recent transactions
    expect(screen.getByText('Grocery Shopping')).toBeInTheDocument();
    expect(screen.getByText('Salary')).toBeInTheDocument();
  });

  it('allows opening the edit account modal and saving changes', async () => {
    localStorage.setItem('token', 'mock-token');
    localStorage.setItem('user', JSON.stringify({ id: '1', email: 'test@example.com' }));

    render(
      <ThemeProvider>
        <AuthProvider>
          <PreferenceProvider>
            <ToastProvider>
              <UIProvider>
                <MemoryRouter>
                  <Dashboard />
                </MemoryRouter>
              </UIProvider>
            </ToastProvider>
          </PreferenceProvider>
        </AuthProvider>
      </ThemeProvider>
    );

    await waitFor(() => {
      expect(screen.getAllByText('Main Bank')[0]).toBeInTheDocument();
    });

    const editBtn = screen.getByRole('button', { name: /Edit Main Bank/i });
    fireEvent.click(editBtn);

    expect(screen.getByRole('heading', { name: /Edit Account/i })).toBeInTheDocument();

    const nameInput = screen.getByLabelText(/Account Name/i) as HTMLInputElement;
    expect(nameInput.value).toBe('Main Bank');

    fireEvent.change(nameInput, { target: { value: 'Main Bank Edited' } });
    const saveBtn = screen.getByRole('button', { name: /Save Changes/i });
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(screen.queryByRole('heading', { name: /Edit Account/i })).not.toBeInTheDocument();
    });
  });

  it('allows opening the edit transaction modal and saving changes', async () => {
    localStorage.setItem('token', 'mock-token');
    localStorage.setItem('user', JSON.stringify({ id: '1', email: 'test@example.com' }));

    render(
      <ThemeProvider>
        <AuthProvider>
          <PreferenceProvider>
            <ToastProvider>
              <UIProvider>
                <MemoryRouter>
                  <Dashboard />
                </MemoryRouter>
              </UIProvider>
            </ToastProvider>
          </PreferenceProvider>
        </AuthProvider>
      </ThemeProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Grocery Shopping')).toBeInTheDocument();
    });

    const editBtn = screen.getByRole('button', { name: /Edit Grocery Shopping/i });
    fireEvent.click(editBtn);

    expect(screen.getByRole('heading', { name: /Edit Transaction/i })).toBeInTheDocument();

    const amountInput = screen.getByLabelText(/Amount/i) as HTMLInputElement;
    expect(amountInput.value).toBe('50');

    fireEvent.change(amountInput, { target: { value: '60' } });
    const saveBtn = screen.getByRole('button', { name: /Save Changes/i });
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(screen.queryByRole('heading', { name: /Edit Transaction/i })).not.toBeInTheDocument();
    });
  });
});
