import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { AccountForm } from './AccountForm';
import type { Account } from '../types/account';

describe('AccountForm', () => {
  it('shows credit limit field only for CREDIT_CARD type', () => {
    render(<AccountForm onSubmit={vi.fn()} onCancel={vi.fn()} />);

    // Initially BANK, credit limit should NOT be there
    expect(screen.queryByLabelText(/Credit Limit/i)).not.toBeInTheDocument();

    // Change to CREDIT_CARD
    const select = screen.getByLabelText(/Account Type/i);
    fireEvent.change(select, { target: { value: 'CREDIT_CARD' } });

    // Now it should be visible
    expect(screen.getByLabelText(/Credit Limit/i)).toBeInTheDocument();
  });

  it('calls onSubmit with form data', async () => {
    const handleSubmit = vi.fn();
    render(<AccountForm onSubmit={handleSubmit} onCancel={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/Account Name/i), { target: { value: 'My Bank' } });
    fireEvent.change(screen.getByLabelText(/Initial Balance/i), { target: { value: '500' } });

    fireEvent.click(screen.getByRole('button', { name: /Create Account/i }));

    expect(handleSubmit).toHaveBeenCalledWith({
      name: 'My Bank',
      type: 'BANK',
      initialBalance: 500,
      balance: 500,
      creditLimit: undefined,
    });
  });

  it('shows lending direction dropdown for FRIEND_LENDING type', () => {
    render(<AccountForm onSubmit={vi.fn()} onCancel={vi.fn()} />);

    // Not visible for BANK
    expect(screen.queryByLabelText(/Direction/i)).not.toBeInTheDocument();

    // Change to FRIEND_LENDING
    const select = screen.getByLabelText(/Account Type/i);
    fireEvent.change(select, { target: { value: 'FRIEND_LENDING' } });

    // Direction dropdown should be visible
    expect(screen.getByLabelText(/Direction/i)).toBeInTheDocument();
  });

  it('submits FRIEND_LENDING with positive balance for "They owe me"', async () => {
    const handleSubmit = vi.fn();
    render(<AccountForm onSubmit={handleSubmit} onCancel={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/Account Name/i), { target: { value: 'Bob' } });
    fireEvent.change(screen.getByLabelText(/Account Type/i), { target: { value: 'FRIEND_LENDING' } });
    fireEvent.change(screen.getByLabelText(/Initial Balance/i), { target: { value: '200' } });

    // Default direction is "They owe me"
    fireEvent.click(screen.getByRole('button', { name: /Create Account/i }));

    expect(handleSubmit).toHaveBeenCalledWith({
      name: 'Bob',
      type: 'FRIEND_LENDING',
      initialBalance: 200,
      balance: 200,
      creditLimit: undefined,
    });
  });

  it('submits FRIEND_LENDING with negative balance for "I owe them"', async () => {
    const handleSubmit = vi.fn();
    render(<AccountForm onSubmit={handleSubmit} onCancel={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/Account Name/i), { target: { value: 'Alice' } });
    fireEvent.change(screen.getByLabelText(/Account Type/i), { target: { value: 'FRIEND_LENDING' } });
    fireEvent.change(screen.getByLabelText(/Initial Balance/i), { target: { value: '300' } });
    fireEvent.change(screen.getByLabelText(/Direction/i), { target: { value: 'I_OWE_THEM' } });

    fireEvent.click(screen.getByRole('button', { name: /Create Account/i }));

    expect(handleSubmit).toHaveBeenCalledWith({
      name: 'Alice',
      type: 'FRIEND_LENDING',
      initialBalance: -300,
      balance: -300,
      creditLimit: undefined,
    });
  });

  it('pre-populates direction from existing FRIEND_LENDING account balance sign', () => {
    const account: Account = {
      id: '1',
      name: 'Bob',
      type: 'FRIEND_LENDING',
      balance: -500,
      initialBalance: -500,
      createdAt: '2025-01-01T00:00:00Z',
    };
    render(<AccountForm onSubmit={vi.fn()} onCancel={vi.fn()} initialData={account} />);

    // Direction should be "I owe them" for negative balance
    expect(screen.getByLabelText(/Direction/i)).toHaveValue('I_OWE_THEM');
    // Balance should show absolute value
    expect(screen.getByLabelText(/Initial Balance/i)).toHaveValue('500');
  });
});
