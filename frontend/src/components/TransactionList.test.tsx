import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { TransactionList } from './TransactionList';
import type { Transaction } from '../types/transaction';

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
    expect(screen.getByText('-$50.00')).toBeInTheDocument();
    expect(screen.getByText('Main Bank')).toBeInTheDocument();
    expect(screen.getByText('🍔')).toBeInTheDocument();
  });

  it('shows empty state when no transactions provided', () => {
    render(<TransactionList transactions={[]} />);
    expect(screen.getByText(/No transactions yet/i)).toBeInTheDocument();
  });
});
