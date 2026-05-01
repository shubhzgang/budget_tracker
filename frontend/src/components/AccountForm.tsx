import React, { useState } from 'react';
import type { AccountType, CreateAccountRequest } from '../types/account';

interface AccountFormProps {
  onSubmit: (data: CreateAccountRequest) => Promise<void>;
  onCancel: () => void;
  isLoading?: boolean;
}

export const AccountForm: React.FC<AccountFormProps> = ({ onSubmit, onCancel, isLoading }) => {
  const [formData, setFormData] = useState<CreateAccountRequest>({
    name: '',
    type: 'BANK',
    balance: 0,
    creditLimit: undefined,
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await onSubmit(formData);
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

      <div className="space-y-1">
        <label htmlFor="initial-balance" className="text-sm font-medium">Initial Balance</label>
        <input
          id="initial-balance"
          required
          type="number"
          step="0.01"
          placeholder="0.00"
          value={formData.balance}
          onChange={(e) => setFormData({ ...formData, balance: parseFloat(e.target.value) })}
          className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
        />
      </div>

      {formData.type === 'CREDIT_CARD' && (
        <div className="space-y-1">
          <label htmlFor="credit-limit" className="text-sm font-medium">Credit Limit</label>
          <input
            id="credit-limit"
            required
            type="number"
            step="0.01"
            placeholder="5000.00"
            value={formData.creditLimit || ''}
            onChange={(e) => setFormData({ ...formData, creditLimit: parseFloat(e.target.value) })}
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
          {isLoading ? 'Creating...' : 'Create Account'}
        </button>
      </div>
    </form>
  );
};
