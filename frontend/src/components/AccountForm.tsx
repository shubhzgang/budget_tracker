import React, { useState } from 'react';
import type { Account, AccountType, CreateAccountRequest } from '../types/account';

interface AccountFormProps {
  initialData?: Account;
  onSubmit: (data: CreateAccountRequest) => Promise<void>;
  onCancel: () => void;
  isLoading?: boolean;
}

type LendingDirection = 'THEY_OWE_ME' | 'I_OWE_THEM';

export const AccountForm: React.FC<AccountFormProps> = ({ initialData, onSubmit, onCancel, isLoading }) => {
  const [formData, setFormData] = useState<{
    name: string;
    type: AccountType;
    balance: string;
    creditLimit: string;
    lendingDirection: LendingDirection;
  }>({
    name: initialData?.name || '',
    type: initialData?.type || 'BANK',
    balance: initialData ? Math.abs(initialData.initialBalance !== undefined ? initialData.initialBalance : initialData.balance).toString() : '',
    creditLimit: initialData?.creditLimit?.toString() || '',
    lendingDirection: initialData?.type === 'FRIEND_LENDING'
      ? (initialData.balance >= 0 ? 'THEY_OWE_ME' : 'I_OWE_THEM')
      : 'THEY_OWE_ME',
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const balanceNum = parseFloat(formData.balance);
    const creditLimitNum = formData.creditLimit ? parseFloat(formData.creditLimit) : undefined;
    const rawBalance = isNaN(balanceNum) ? 0 : balanceNum;

    // For FRIEND_LENDING: apply sign based on direction
    const signedBalance = formData.type === 'FRIEND_LENDING'
      ? (formData.lendingDirection === 'I_OWE_THEM' ? -Math.abs(rawBalance) : Math.abs(rawBalance))
      : rawBalance;

    const payload: CreateAccountRequest = {
      name: formData.name,
      type: formData.type,
      initialBalance: signedBalance,
      balance: signedBalance,
      creditLimit: isNaN(creditLimitNum as number) ? undefined : creditLimitNum,
    };

    await onSubmit(payload);
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="space-y-1">
        <label htmlFor="account-name" className="text-sm font-medium">Account Name</label>
        <input
          id="account-name"
          required
          type="text"
          placeholder="e.g. Chase Sapphire"
          value={formData.name}
          onChange={(e) => setFormData({ ...formData, name: e.target.value })}
          className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
        />
      </div>

      <div className="space-y-1">
        <label htmlFor="account-type" className="text-sm font-medium">Account Type</label>
        <select
          id="account-type"
          value={formData.type}
          onChange={(e) => setFormData({ ...formData, type: e.target.value as AccountType })}
          className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
        >
          <option value="BANK">Bank</option>
          <option value="CASH">Cash</option>
          <option value="CREDIT_CARD">Credit Card</option>
          <option value="FRIEND_LENDING">Friend/Lending</option>
        </select>
      </div>

      {formData.type === 'FRIEND_LENDING' && (
        <div className="space-y-1">
          <label htmlFor="lending-direction" className="text-sm font-medium">Direction</label>
          <select
            id="lending-direction"
            value={formData.lendingDirection}
            onChange={(e) => setFormData({ ...formData, lendingDirection: e.target.value as LendingDirection })}
            className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
          >
            <option value="THEY_OWE_ME">They owe me</option>
            <option value="I_OWE_THEM">I owe them</option>
          </select>
        </div>
      )}

      <div className="space-y-1">
        <label htmlFor="initial-balance" className="text-sm font-medium">Initial Balance</label>
        <input
          id="initial-balance"
          required
          type="text"
          inputMode="decimal"
          placeholder="123"
          value={formData.balance}
          onChange={(e) => {
            const val = e.target.value;
            if (val === '' || /^\d*\.?\d*$/.test(val)) {
              setFormData({ ...formData, balance: val });
            }
          }}
          className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
        />
      </div>

      {formData.type === 'CREDIT_CARD' && (
        <div className="space-y-1">
          <label htmlFor="credit-limit" className="text-sm font-medium">Credit Limit</label>
          <input
            id="credit-limit"
            required
            type="text"
            inputMode="decimal"
            placeholder="5000"
            value={formData.creditLimit}
            onChange={(e) => {
              const val = e.target.value;
              if (val === '' || /^\d*\.?\d*$/.test(val)) {
                setFormData({ ...formData, creditLimit: val });
              }
            }}
            className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
          />
        </div>
      )}

      <div className="flex gap-3 pt-4">
        <button
          type="button"
          onClick={onCancel}
          className="flex-1 px-4 py-2 border border-input bg-background hover:bg-secondary rounded-md transition-colors"
        >
          Cancel
        </button>
        <button
          type="submit"
          disabled={isLoading}
          className="flex-1 px-4 py-2 bg-primary text-primary-foreground rounded-md font-medium hover:opacity-90 transition-opacity disabled:opacity-50"
        >
          {isLoading ? (initialData ? 'Saving...' : 'Creating...') : (initialData ? 'Save Changes' : 'Create Account')}
        </button>
      </div>
    </form>
  );
};
