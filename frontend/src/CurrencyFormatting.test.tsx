import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { usePreferences } from './context/PreferenceContext';
import { BalanceCard } from './components/BalanceCard';
import { TransactionList } from './components/TransactionList';
import type { Account } from './types/account';
import type { Transaction } from './types/transaction';

// Mock usePreferences hook
vi.mock('./context/PreferenceContext', () => ({
  usePreferences: vi.fn(),
}));

describe('Currency Formatting across components', () => {
  const mockAccount: Account = {
    id: '1',
    name: 'Test Bank',
    type: 'BANK',
    balance: 1234.56,
    createdAt: new Date().toISOString(),
  };

  const mockTransactions: Transaction[] = [
    {
      id: 't1',
      amount: 123.45,
      type: 'EXPENSE',
      description: 'Test Expense',
      transactionDate: '2026-05-01T00:00:00Z',
      accountId: '1',
      account: { id: '1', name: 'Test Bank', balance: 0, type: 'BANK', createdAt: '' },
      categoryId: 'c1',
      category: { id: 'c1', name: 'Food', icon: '🍔', isDefault: true, createdAt: '' },
      createdAt: ''
    }
  ];

  it('renders BalanceCard with ₹ symbol', () => {
    vi.mocked(usePreferences).mockReturnValue({
      preferences: { currencySymbol: '₹' } as any,
      isLoading: false,
      updatePreferences: vi.fn(),
      refreshPreferences: vi.fn(),
    });

    render(<BalanceCard account={mockAccount} />);
    expect(screen.getByText('₹1,234.56')).toBeInTheDocument();
  });

  it('renders BalanceCard with $ symbol', () => {
    vi.mocked(usePreferences).mockReturnValue({
      preferences: { currencySymbol: '$' } as any,
      isLoading: false,
      updatePreferences: vi.fn(),
      refreshPreferences: vi.fn(),
    });

    render(<BalanceCard account={mockAccount} />);
    expect(screen.getByText('$1,234.56')).toBeInTheDocument();
  });

  it('renders TransactionList with € symbol', () => {
    vi.mocked(usePreferences).mockReturnValue({
      preferences: { currencySymbol: '€' } as any,
      isLoading: false,
      updatePreferences: vi.fn(),
      refreshPreferences: vi.fn(),
    });

    render(<TransactionList transactions={mockTransactions} />);
    expect(screen.getByText('-€123.45')).toBeInTheDocument();
  });
});
