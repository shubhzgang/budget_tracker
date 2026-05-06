import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { BalanceCard } from './BalanceCard';
import type { Account } from '../types/account';

// Mock usePreferences
vi.mock('../context/PreferenceContext', () => ({
  usePreferences: () => ({
    preferences: { currencySymbol: '₹' },
    isLoading: false,
    updatePreferences: vi.fn(),
    refreshPreferences: vi.fn(),
  }),
}));

describe('BalanceCard', () => {
  const mockAccount: Account = {
    id: '1',
    name: 'Test Bank',
    type: 'BANK',
    balance: 1000.50,
    createdAt: new Date().toISOString(),
  };

  it('renders account name and formatted balance', () => {
    render(<BalanceCard account={mockAccount} />);
    expect(screen.getByText('Test Bank')).toBeInTheDocument();
    // Non-breaking space might be used by Intl.NumberFormat in some environments, 
    // but here we are using our custom formatter which just prepends the symbol.
    expect(screen.getByText('₹1,000.50')).toBeInTheDocument();
  });

  it('shows credit utilization for credit cards', () => {
    const ccAccount: Account = {
      ...mockAccount,
      type: 'CREDIT_CARD',
      balance: 500,
      creditLimit: 1000,
    };
    render(<BalanceCard account={ccAccount} />);
    expect(screen.getByText('50.0%')).toBeInTheDocument();
    expect(screen.getByText('Limit: ₹1,000.00')).toBeInTheDocument();
  });

  it('shows "They owe you" for positive lending balance', () => {
    const lendingAccount: Account = {
      ...mockAccount,
      type: 'FRIEND_LENDING',
      balance: 100,
    };
    render(<BalanceCard account={lendingAccount} />);
    expect(screen.getByText('They owe you')).toBeInTheDocument();
  });

  it('shows "Debt: " prefix for credit cards', () => {
    const ccAccount: Account = {
      ...mockAccount,
      type: 'CREDIT_CARD',
      balance: 450,
    };
    render(<BalanceCard account={ccAccount} />);
    expect(screen.getByText(/Debt: ₹450.00/i)).toBeInTheDocument();
  });
});
