import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { AccountForm } from './AccountForm';

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
      balance: 500,
      creditLimit: undefined,
    });
  });
});
