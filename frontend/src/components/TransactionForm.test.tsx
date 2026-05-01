import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { TransactionForm } from './TransactionForm';
import type { Account } from '../types/account';
import type { Category } from '../types/category';
import type { Label } from '../types/label';

describe('TransactionForm', () => {
  const mockAccounts: Account[] = [
    { id: '1', name: 'Bank 1', balance: 1000, type: 'BANK', createdAt: '' },
    { id: '2', name: 'Bank 2', balance: 500, type: 'BANK', createdAt: '' },
  ];
  const mockCategories: Category[] = [
    { id: 'c1', name: 'Food', icon: '🍔', isDefault: true, createdAt: '' },
  ];
  const mockLabels: Label[] = [
    { id: 'l1', name: 'Personal', isDefault: true, createdAt: '' },
  ];

  it('shows toAccount field only for TRANSFER type', () => {
    render(
      <TransactionForm
        accounts={mockAccounts}
        categories={mockCategories}
        labels={mockLabels}
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    expect(screen.queryByLabelText(/To Account/i)).not.toBeInTheDocument();

    const typeSelect = screen.getByLabelText(/Type/i);
    fireEvent.change(typeSelect, { target: { value: 'TRANSFER' } });

    expect(screen.getByLabelText(/To Account/i)).toBeInTheDocument();
  });

  it('submits form with correct data', async () => {
    const handleSubmit = vi.fn();
    render(
      <TransactionForm
        accounts={mockAccounts}
        categories={mockCategories}
        labels={mockLabels}
        onSubmit={handleSubmit}
        onCancel={vi.fn()}
      />
    );

    fireEvent.change(screen.getByLabelText(/Amount/i), { target: { value: '100' } });
    fireEvent.change(screen.getByLabelText(/Description/i), { target: { value: 'Test description' } });
    
    fireEvent.click(screen.getByRole('button', { name: /Add Transaction/i }));

    expect(handleSubmit).toHaveBeenCalledWith(expect.objectContaining({
      amount: 100,
      description: 'Test description',
      type: 'EXPENSE'
    }));
  });
});
