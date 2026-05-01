import React, { useState } from 'react';
import type { Account } from '../types/account';
import type { Category } from '../types/category';
import type { Label } from '../types/label';
import type { TransactionType, CreateTransactionRequest } from '../types/transaction';

interface TransactionFormProps {
  accounts: Account[];
  categories: Category[];
  labels: Label[];
  onSubmit: (data: CreateTransactionRequest) => Promise<void>;
  onCancel: () => void;
  isLoading?: boolean;
}

export const TransactionForm: React.FC<TransactionFormProps> = ({
  accounts,
  categories,
  labels,
  onSubmit,
  onCancel,
  isLoading
}) => {
  const [formData, setFormData] = useState<CreateTransactionRequest>({
    amount: 0,
    type: 'EXPENSE',
    transactionDate: new Date().toISOString().split('T')[0],
    accountId: accounts[0]?.id || '',
    toAccountId: '',
    categoryId: categories[0]?.id || '',
    labelId: labels.find(l => l.isDefault)?.id || labels[0]?.id || '',
    description: ''
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    // Ensure transactionDate is ISO 8601 string (with time and offset) for backend OffsetDateTime
    const payload = {
      ...formData,
      transactionDate: formData.transactionDate.includes('T') 
        ? formData.transactionDate 
        : `${formData.transactionDate}T00:00:00Z`
    };
    
    await onSubmit(payload);
  };

  const showCategory = formData.type === 'EXPENSE' || formData.type === 'INCOME';
  const showToAccount = formData.type === 'TRANSFER';

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-1">
          <label htmlFor="trans-type" className="text-sm font-medium">Type</label>
          <select
            id="trans-type"
            value={formData.type}
            onChange={(e) => setFormData({ ...formData, type: e.target.value as TransactionType })}
            className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
          >
            <option value="EXPENSE">Expense</option>
            <option value="INCOME">Income</option>
            <option value="TRANSFER">Transfer</option>
            <option value="LEND">Lend</option>
            <option value="BORROW">Borrow</option>
          </select>
        </div>
        <div className="space-y-1">
          <label htmlFor="trans-amount" className="text-sm font-medium">Amount</label>
          <input
            id="trans-amount"
            required
            type="number"
            step="0.01"
            min="0"
            value={formData.amount}
            onChange={(e) => setFormData({ ...formData, amount: parseFloat(e.target.value) })}
            className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
          />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-1">
          <label htmlFor="trans-date" className="text-sm font-medium">Date</label>
          <input
            id="trans-date"
            required
            type="date"
            value={formData.transactionDate}
            onChange={(e) => setFormData({ ...formData, transactionDate: e.target.value })}
            className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
          />
        </div>
        <div className="space-y-1">
          <label htmlFor="trans-account" className="text-sm font-medium">Account</label>
          <select
            id="trans-account"
            required
            value={formData.accountId}
            onChange={(e) => setFormData({ ...formData, accountId: e.target.value })}
            className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
          >
            {accounts.map(acc => (
              <option key={acc.id} value={acc.id}>{acc.name}</option>
            ))}
          </select>
        </div>
      </div>

      {showToAccount && (
        <div className="space-y-1 animate-in slide-in-from-top-1 duration-200">
          <label htmlFor="trans-to-account" className="text-sm font-medium">To Account</label>
          <select
            id="trans-to-account"
            required
            value={formData.toAccountId}
            onChange={(e) => setFormData({ ...formData, toAccountId: e.target.value })}
            className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
          >
            <option value="">Select destination...</option>
            {accounts.filter(a => a.id !== formData.accountId).map(acc => (
              <option key={acc.id} value={acc.id}>{acc.name}</option>
            ))}
          </select>
        </div>
      )}

      {showCategory && (
        <div className="space-y-1 animate-in slide-in-from-top-1 duration-200">
          <label htmlFor="trans-category" className="text-sm font-medium">Category</label>
          <select
            id="trans-category"
            value={formData.categoryId}
            onChange={(e) => setFormData({ ...formData, categoryId: e.target.value })}
            className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
          >
            {categories.map(cat => (
              <option key={cat.id} value={cat.id}>{cat.icon} {cat.name}</option>
            ))}
          </select>
        </div>
      )}

      <div className="space-y-1">
        <label htmlFor="trans-label" className="text-sm font-medium">Label</label>
        <select
          id="trans-label"
          value={formData.labelId}
          onChange={(e) => setFormData({ ...formData, labelId: e.target.value })}
          className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
        >
          <option value="">No Label</option>
          {labels.map(lbl => (
            <option key={lbl.id} value={lbl.id}>{lbl.name}</option>
          ))}
        </select>
      </div>

      <div className="space-y-1">
        <label htmlFor="trans-desc" className="text-sm font-medium">Description (Optional)</label>
        <input
          id="trans-desc"
          type="text"
          value={formData.description}
          onChange={(e) => setFormData({ ...formData, description: e.target.value })}
          className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
          placeholder="What was this for?"
        />
      </div>

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
          {isLoading ? 'Saving...' : 'Add Transaction'}
        </button>
      </div>
    </form>
  );
};
