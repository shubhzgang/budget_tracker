import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { TransactionList } from './TransactionList';
import type { ActivityItem } from '../types/activity';

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
  const mockTransactions: ActivityItem[] = [
    {
      id: 't1',
      kind: 'TRANSACTION',
      amount: 50,
      type: 'EXPENSE',
      description: 'Groceries',
      transactionDate: '2026-05-01T00:00:00Z',
      account: { id: '1', name: 'Main Bank', balance: 0, type: 'BANK', createdAt: '' },
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
    const transferTransaction: ActivityItem = {
      id: 't-transfer',
      kind: 'TRANSFER',
      type: 'TRANSFER',
      fromAmount: 200,
      description: 'Monthly savings',
      transactionDate: '2026-05-15T00:00:00Z',
      account: { id: '1', name: 'Main Bank', balance: 0, type: 'BANK', createdAt: '' },
      toAccount: { id: '2', name: 'Savings', balance: 0, type: 'BANK', createdAt: '' },
      createdAt: ''
    };
    render(<TransactionList transactions={[transferTransaction]} />);

    expect(screen.getByText('Monthly savings')).toBeInTheDocument();
    expect(screen.getByText('Main Bank → Savings')).toBeInTheDocument();
    expect(screen.getByText('TRANSFER')).toBeInTheDocument();
  });

  it('renders transfer without description as "Transfer"', () => {
    const transferTransaction: ActivityItem = {
      id: 't-transfer-no-desc',
      kind: 'TRANSFER',
      type: 'TRANSFER',
      fromAmount: 100,
      transactionDate: '2026-05-15T00:00:00Z',
      account: { id: '1', name: 'Cash', balance: 0, type: 'CASH', createdAt: '' },
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

  it('displays adjustment badge when adjustment > 0', () => {
    const transferWithAdjustment: ActivityItem = {
      id: 't-adj',
      kind: 'TRANSFER',
      type: 'TRANSFER',
      fromAmount: 95,
      toAmount: 100,
      adjustment: 5,
      description: 'CC payment',
      transactionDate: '2026-05-15T00:00:00Z',
      account: { id: '1', name: 'Main Bank', balance: 0, type: 'BANK', createdAt: '' },
      toAccount: { id: '2', name: 'Visa CC', balance: 0, type: 'CREDIT_CARD', createdAt: '' },
      createdAt: ''
    };
    render(<TransactionList transactions={[transferWithAdjustment]} />);

    expect(screen.getByText('CC payment')).toBeInTheDocument();
    // Adjustment badge should show "+₹5.00 adj"
    expect(screen.getByText('+₹5.00 adj')).toBeInTheDocument();
  });

  it('does not render delete button when onDelete is not provided', () => {
    render(<TransactionList transactions={mockTransactions} />);
    expect(screen.queryByTitle('Delete')).not.toBeInTheDocument();
  });

  it('renders delete button and fires onDelete when clicked', () => {
    const onDelete = vi.fn();
    render(<TransactionList transactions={mockTransactions} onDelete={onDelete} />);

    const deleteButton = screen.getByTitle('Delete');
    expect(deleteButton).toBeInTheDocument();

    fireEvent.click(deleteButton);
    expect(onDelete).toHaveBeenCalledTimes(1);
    expect(onDelete).toHaveBeenCalledWith(mockTransactions[0]);
  });
});
