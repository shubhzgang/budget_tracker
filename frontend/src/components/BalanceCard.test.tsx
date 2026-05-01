import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { BalanceCard } from './BalanceCard';
import type { Account } from '../types/account';

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
    expect(screen.getByText('$1,000.50')).toBeInTheDocument();
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
    expect(screen.getByText('Limit: $1,000.00')).toBeInTheDocument();
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

  it('shows "You owe them" for negative lending balance', () => {
    const lendingAccount: Account = {
      ...mockAccount,
      type: 'FRIEND_LENDING',
      balance: -100,
    };
    render(<BalanceCard account={lendingAccount} />);
    expect(screen.getByText('You owe them')).toBeInTheDocument();
  });
});
