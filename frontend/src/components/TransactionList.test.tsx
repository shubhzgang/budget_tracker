import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { TransactionList } from './TransactionList';
import type { Transaction } from '../types/transaction';

// Mock usePreferences
vi.mock('../context/PreferenceContext', () => ({
  usePreferences: () => ({
    preferences: { currencySymbol: '₹' },
    isLoading: false,
    updatePreferences: vi.fn(),
    refreshPreferences: vi.fn(),
  }),
}));

describe('TransactionList', () => {
  const mockTransactions: Transaction[] = [
    {
      id: 't1',
      amount: 50,
      type: 'EXPENSE',
      description: 'Groceries',
      transactionDate: '2026-05-01T00:00:00Z',
      accountId: '1',
      account: { id: '1', name: 'Main Bank', balance: 0, type: 'BANK', createdAt: '' },
      categoryId: 'c1',
      category: { id: 'c1', name: 'Food', icon: '🍔', isDefault: true, createdAt: '' },
      createdAt: ''
    }
  ];

  it('renders transactions with correct formatting', () => {
    render(<TransactionList transactions={mockTransactions} />);
    
    expect(screen.getByText('Groceries')).toBeInTheDocument();
    expect(screen.getByText('-₹50.00')).toBeInTheDocument();
    expect(screen.getByText('Main Bank')).toBeInTheDocument();
    expect(screen.getByText('🍔')).toBeInTheDocument();
  });

  it('shows empty state when no transactions provided', () => {
    render(<TransactionList transactions={[]} />);
    expect(screen.getByText(/No transactions yet/i)).toBeInTheDocument();
  });

  it('renders transfer with description and arrow visualization', () => {
    const transferTransaction: Transaction = {
      id: 't-transfer',
      amount: 200,
      type: 'TRANSFER',
      description: 'Monthly savings',
      transactionDate: '2026-05-15T00:00:00Z',
      accountId: '1',
      account: { id: '1', name: 'Main Bank', balance: 0, type: 'BANK', createdAt: '' },
      toAccountId: '2',
      toAccount: { id: '2', name: 'Savings', balance: 0, type: 'BANK', createdAt: '' },
      createdAt: ''
    };
    render(<TransactionList transactions={[transferTransaction]} />);

    expect(screen.getByText('Monthly savings')).toBeInTheDocument();
    expect(screen.getByText('Main Bank → Savings')).toBeInTheDocument();
    expect(screen.getByText('TRANSFER')).toBeInTheDocument();
  });

  it('renders transfer without description as "Transfer"', () => {
    const transferTransaction: Transaction = {
      id: 't-transfer-no-desc',
      amount: 100,
      type: 'TRANSFER',
      transactionDate: '2026-05-15T00:00:00Z',
      accountId: '1',
      account: { id: '1', name: 'Cash', balance: 0, type: 'CASH', createdAt: '' },
      toAccountId: '2',
      toAccount: { id: '2', name: 'Main Bank', balance: 0, type: 'BANK', createdAt: '' },
      createdAt: ''
    };
    render(<TransactionList transactions={[transferTransaction]} />);

    expect(screen.getByText('Transfer')).toBeInTheDocument();
    expect(screen.getByText('Cash → Main Bank')).toBeInTheDocument();
  });

  it('renders non-transfer transactions without arrow', () => {
    render(<TransactionList transactions={mockTransactions} />);
    expect(screen.getByText('Main Bank')).toBeInTheDocument();
    // Should not have arrow for non-transfer types
    expect(screen.queryByText(/→/)).not.toBeInTheDocument();
  });
});
