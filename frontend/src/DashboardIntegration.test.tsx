import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import { Dashboard } from './pages/Dashboard';

describe('Dashboard Integration', () => {
  it('fetches and displays accounts grouped by type', async () => {
    // Auth context needs a user/token to render Dashboard
    localStorage.setItem('token', 'mock-token');
    localStorage.setItem('user', JSON.stringify({ id: '1', email: 'test@example.com' }));

    render(
      <ThemeProvider>
        <AuthProvider>
          <MemoryRouter>
            <Dashboard />
          </MemoryRouter>
        </AuthProvider>
      </ThemeProvider>
    );

    // Should show Net Worth (calculated from MSW mock in handlers.ts: 1000 + 50 = 1050)
    await waitFor(() => {
      expect(screen.getByText('$1,050.00')).toBeInTheDocument();
    });

    // Should show account names
    expect(screen.getByText('Main Bank')).toBeInTheDocument();
    expect(screen.getByText('Cash')).toBeInTheDocument();

    // Should show group headers (using getAllByText because it's in both header and card tag)
    expect(screen.getAllByText(/BANK/i)[0]).toBeInTheDocument();
    expect(screen.getAllByText(/CASH/i)[0]).toBeInTheDocument();
    
    // Verify specific header role for grouping
    expect(screen.getByRole('heading', { name: /Dashboard/i })).toBeInTheDocument();
  });
});
