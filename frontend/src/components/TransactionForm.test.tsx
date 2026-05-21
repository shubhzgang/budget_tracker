import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { TransactionForm } from './TransactionForm';
import type { Account } from '../types/account';
import type { Category } from '../types/category';
import type { Label } from '../types/label';

vi.mock('../context/PreferenceContext', () => ({
  usePreferences: () => ({
    preferences: {
      defaultAccountId: '1',
      defaultTransactionType: 'EXPENSE',
      defaultCategoryId: 'c1',
      defaultLabelId: 'l1'
    }
  })
}));

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

  it('prevents submission when amount is empty or zero', async () => {
    const handleSubmit = vi.fn();
    const alertMock = vi.spyOn(window, 'alert').mockImplementation(() => {});
    
    const { container } = render(
      <TransactionForm
        accounts={mockAccounts}
        categories={mockCategories}
        labels={mockLabels}
        onSubmit={handleSubmit}
        onCancel={vi.fn()}
      />
    );

    // Initial amount is '' in state
    const form = container.querySelector('form');
    if (form) {
      fireEvent.submit(form);
    }

    expect(alertMock).toHaveBeenCalledWith('Amount must be greater than zero');
    expect(handleSubmit).not.toHaveBeenCalled();
    
    // Test with zero string
    fireEvent.change(screen.getByLabelText(/Amount/i), { target: { value: '0' } });
    if (form) {
      fireEvent.submit(form);
    }
    expect(alertMock).toHaveBeenCalledWith('Amount must be greater than zero');
    
    alertMock.mockRestore();
  });

  it('computes toAmount when fromAmount and adjustment are entered for TRANSFER', () => {
    render(
      <TransactionForm
        accounts={mockAccounts}
        categories={mockCategories}
        labels={mockLabels}
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    // Switch to TRANSFER
    fireEvent.change(screen.getByLabelText(/Type/i), { target: { value: 'TRANSFER' } });

    const fromInput = screen.getByLabelText(/From Amount/i);
    const adjInput = screen.getByLabelText(/Adjustment/i);
    const toInput = screen.getByLabelText(/To Amount/i);

    fireEvent.change(fromInput, { target: { value: '100' } });
    fireEvent.change(adjInput, { target: { value: '5' } });

    expect(toInput).toHaveValue('105');
  });

  it('computes adjustment when fromAmount and toAmount are entered for TRANSFER', () => {
    render(
      <TransactionForm
        accounts={mockAccounts}
        categories={mockCategories}
        labels={mockLabels}
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    // Switch to TRANSFER
    fireEvent.change(screen.getByLabelText(/Type/i), { target: { value: 'TRANSFER' } });

    const fromInput = screen.getByLabelText(/From Amount/i);
    const toInput = screen.getByLabelText(/To Amount/i);
    const adjInput = screen.getByLabelText(/Adjustment/i);

    fireEvent.change(fromInput, { target: { value: '100' } });
    fireEvent.change(toInput, { target: { value: '105' } });

    expect(adjInput).toHaveValue('5');
  });

  it('computes fromAmount when toAmount and adjustment are entered for TRANSFER', () => {
    render(
      <TransactionForm
        accounts={mockAccounts}
        categories={mockCategories}
        labels={mockLabels}
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    // Switch to TRANSFER
    fireEvent.change(screen.getByLabelText(/Type/i), { target: { value: 'TRANSFER' } });

    const toInput = screen.getByLabelText(/To Amount/i);
    const adjInput = screen.getByLabelText(/Adjustment/i);
    const fromInput = screen.getByLabelText(/From Amount/i);

    fireEvent.change(toInput, { target: { value: '105' } });
    fireEvent.change(adjInput, { target: { value: '5' } });

    expect(fromInput).toHaveValue('100');
  });

  it('rejects TRANSFER submission when less than two amount fields are filled', () => {
    const handleSubmit = vi.fn();
    const alertMock = vi.spyOn(window, 'alert').mockImplementation(() => {});

    const { container } = render(
      <TransactionForm
        accounts={mockAccounts}
        categories={mockCategories}
        labels={mockLabels}
        onSubmit={handleSubmit}
        onCancel={vi.fn()}
      />
    );

    // Switch to TRANSFER
    fireEvent.change(screen.getByLabelText(/Type/i), { target: { value: 'TRANSFER' } });

    // Fill only From Amount
    fireEvent.change(screen.getByLabelText(/From Amount/i), { target: { value: '100' } });
    
    // Select to account
    fireEvent.change(screen.getByLabelText(/To Account/i), { target: { value: '2' } });

    const form = container.querySelector('form');
    if (form) {
      fireEvent.submit(form);
    }

    expect(alertMock).toHaveBeenCalledWith('At least two of From Amount, To Amount, or Adjustment must be valid positive numbers');
    expect(handleSubmit).not.toHaveBeenCalled();

    alertMock.mockRestore();
  });

  it('submits transfer with correct payload to transfers endpoint', async () => {
    const handleSubmit = vi.fn();

    const { container } = render(
      <TransactionForm
        accounts={mockAccounts}
        categories={mockCategories}
        labels={mockLabels}
        onSubmit={handleSubmit}
        onCancel={vi.fn()}
      />
    );

    // Switch to TRANSFER
    fireEvent.change(screen.getByLabelText(/Type/i), { target: { value: 'TRANSFER' } });

    // Select to account
    fireEvent.change(screen.getByLabelText(/To Account/i), { target: { value: '2' } });

    // Fill fromAmount and adjustment
    fireEvent.change(screen.getByLabelText(/From Amount/i), { target: { value: '95' } });
    fireEvent.change(screen.getByLabelText(/Adjustment/i), { target: { value: '5' } });

    const form = container.querySelector('form');
    if (form) {
      fireEvent.submit(form);
    }

    expect(handleSubmit).toHaveBeenCalledWith(expect.objectContaining({
      type: 'TRANSFER',
      fromAccountId: '1',
      toAccountId: '2',
      fromAmount: 95,
      adjustment: 5
    }));
    // Note: only the edited fields are included in the payload, which matches TransferRequest constraints
    expect(handleSubmit.mock.calls[0][0]).not.toHaveProperty('toAmount');
  });

  it('does not allow non-numeric input for transfer amount fields', () => {
    render(
      <TransactionForm
        accounts={mockAccounts}
        categories={mockCategories}
        labels={mockLabels}
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    // Switch to TRANSFER
    fireEvent.change(screen.getByLabelText(/Type/i), { target: { value: 'TRANSFER' } });

    const fromInput = screen.getByLabelText(/From Amount/i) as HTMLInputElement;

    // Attempt to type non-numeric character
    fireEvent.change(fromInput, { target: { value: 'abc' } });
    expect(fromInput.value).toBe('');

    // Attempt to type a valid number
    fireEvent.change(fromInput, { target: { value: '123.45' } });
    expect(fromInput.value).toBe('123.45');

    // Attempt to type non-numeric character afterwards
    fireEvent.change(fromInput, { target: { value: '123.45a' } });
    expect(fromInput.value).toBe('123.45');
  });
});
