import React, { useState, useEffect, useRef, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { usePreferences } from '../context/PreferenceContext';
import type { Account } from '../types/account';
import type { Category } from '../types/category';
import type { Label } from '../types/label';
import type { ActivityItem } from '../types/activity';

interface MultiSelectProps {
  options: { id: string; name: string }[];
  selectedIds: string[];
  onChange: (selected: string[]) => void;
}

const MultiSelect: React.FC<MultiSelectProps> = ({ options, selectedIds, onChange }) => {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const [dropdownRect, setDropdownRect] = useState<{ top: number; left: number; width: number } | null>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      const target = e.target as Node;
      if (
        containerRef.current && !containerRef.current.contains(target) &&
        dropdownRef.current && !dropdownRef.current.contains(target)
      ) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleToggle = (e: React.MouseEvent<HTMLButtonElement>) => {
    if (!isOpen) {
      const rect = e.currentTarget.getBoundingClientRect();
      setDropdownRect({ top: rect.bottom + 4, left: rect.left, width: rect.width });
    }
    setIsOpen(v => !v);
  };

  const toggle = useCallback((id: string) => {
    onChange(selectedIds.includes(id)
      ? selectedIds.filter(s => s !== id)
      : [...selectedIds, id]
    );
  }, [selectedIds, onChange]);

  const dropdown = isOpen && dropdownRect ? (
    <div
      ref={dropdownRef}
      style={{ position: 'fixed', top: dropdownRect.top, left: dropdownRect.left, width: dropdownRect.width, zIndex: 9999 }}
      className="bg-card border border-border rounded-md shadow-lg max-h-60 overflow-y-auto"
      data-testid="label-dropdown"
    >
      {options.map(opt => {
        const checked = selectedIds.includes(opt.id);
        return (
          <button
            key={opt.id}
            type="button"
            onClick={() => toggle(opt.id)}
            className="w-full px-3 py-2 text-left text-sm hover:bg-secondary flex items-center gap-2"
          >
            <input type="checkbox" checked={checked} readOnly className="rounded" />
            {opt.name}
          </button>
        );
      })}
    </div>
  ) : null;

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        data-testid="label-select-toggle"
        onClick={handleToggle}
        className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none text-left text-sm min-h-[38px] flex flex-wrap items-center gap-1"
      >
        {selectedIds.length === 0 && <span className="text-muted-foreground">Select labels...</span>}
        {selectedIds.map(id => {
          const option = options.find(o => o.id === id);
          if (!option) return null;
          return (
            <span key={id} className="inline-flex items-center gap-1 px-2 py-0.5 bg-primary/10 text-primary text-[10px] rounded-full font-medium">
              {option.name}
              <button type="button" onClick={(e) => { e.stopPropagation(); toggle(id); }} className="hover:text-primary/70">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" className="w-3 h-3">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </span>
          );
        })}
      </button>
      {createPortal(dropdown, document.body)}
    </div>
  );
};

interface TransactionFormProps {
  accounts: Account[];
  categories: Category[];
  labels: Label[];
  initialData?: ActivityItem;
  onSubmit: (data: any) => Promise<void>;
  onCancel: () => void;
  isLoading?: boolean;
}

export const TransactionForm: React.FC<TransactionFormProps> = ({
  accounts,
  categories,
  labels,
  initialData,
  onSubmit,
  onCancel,
  isLoading
}) => {
  const { preferences } = usePreferences();

  const isEditMode = !!initialData;

  const [formData, setFormData] = useState(() => {
    if (initialData) {
      return {
        amount: initialData.amount != null ? initialData.amount.toString() : '',
        fromAmount: initialData.fromAmount != null ? initialData.fromAmount.toString() : '',
        toAmount: initialData.toAmount != null ? initialData.toAmount.toString() : '',
        adjustment: initialData.adjustment != null ? initialData.adjustment.toString() : '',
        type: initialData.type,
        transactionDate: initialData.transactionDate ? initialData.transactionDate.split('T')[0] : new Date().toISOString().split('T')[0],
        accountId: initialData.account?.id || '',
        toAccountId: initialData.toAccount?.id || '',
        categoryId: initialData.category?.id || '',
        labelIds: initialData.labels?.map(l => l.id) || [],
        description: initialData.description || ''
      };
    }

    // 1. Initial fallbacks
    const initialType = preferences?.defaultTransactionType || 'EXPENSE';
    const initialAccount = preferences?.defaultAccountId || accounts[0]?.id || '';
    const initialCategory = preferences?.defaultCategoryId || categories[0]?.id || '';
    const initialLabelId = preferences?.defaultLabelId || labels.find(l => l.isDefault)?.id;

    // 2. Validate fallbacks exist in current props
    const validatedAccount = accounts.some(a => a.id === initialAccount) ? initialAccount : (accounts[0]?.id || '');
    const validatedCategory = categories.some(c => c.id === initialCategory) ? initialCategory : (categories[0]?.id || '');
    const initialLabels = initialLabelId && labels.some(l => l.id === initialLabelId) ? [initialLabelId] : [];

    return {
      amount: '',
      fromAmount: '',
      toAmount: '',
      adjustment: '',
      type: initialType as any,
      transactionDate: new Date().toISOString().split('T')[0],
      accountId: validatedAccount,
      toAccountId: '',
      categoryId: validatedCategory,
      labelIds: initialLabels,
      description: ''
    };
  });

  const [lastEdited, setLastEdited] = useState<('fromAmount' | 'toAmount' | 'adjustment')[]>(() => {
    if (initialData?.type === 'TRANSFER' || initialData?.kind === 'TRANSFER') {
      const fields: ('fromAmount' | 'toAmount' | 'adjustment')[] = [];
      if (initialData.fromAmount != null) fields.push('fromAmount');
      if (initialData.adjustment != null) fields.push('adjustment');
      if (initialData.toAmount != null && fields.length < 2) fields.push('toAmount');
      return fields;
    }
    return [];
  });

  useEffect(() => {
    if (!formData.accountId && accounts.length > 0) {
      setFormData(prev => ({
        ...prev,
        accountId: preferences?.defaultAccountId && accounts.some(a => a.id === preferences.defaultAccountId)
          ? preferences.defaultAccountId
          : accounts[0].id
      }));
    }
  }, [accounts, preferences, formData.accountId]);

  const handleAmountChange = (field: 'fromAmount' | 'toAmount' | 'adjustment', value: string) => {
    if (value !== '' && !/^\d*\.?\d*$/.test(value)) return;

    const newFormData = { ...formData, [field]: value };
    
    let newLastEdited = [field, ...lastEdited.filter(f => f !== field)];
    if (newLastEdited.length > 2) {
      newLastEdited = newLastEdited.slice(0, 2);
    }
    setLastEdited(newLastEdited);

    if (newLastEdited.length === 2) {
      const field1 = newLastEdited[0];
      const field2 = newLastEdited[1];
      const val1 = parseFloat(newFormData[field1]);
      const val2 = parseFloat(newFormData[field2]);

      if (!isNaN(val1) && !isNaN(val2)) {
        const targetField = (['fromAmount', 'toAmount', 'adjustment'] as const).find(
          f => f !== field1 && f !== field2
        )!;

        let computedValue = 0;
        if (targetField === 'adjustment') {
          const fromVal = field1 === 'fromAmount' ? val1 : val2;
          const toVal = field1 === 'toAmount' ? val1 : val2;
          computedValue = toVal - fromVal;
        } else if (targetField === 'toAmount') {
          const fromVal = field1 === 'fromAmount' ? val1 : val2;
          const adjVal = field1 === 'adjustment' ? val1 : val2;
          computedValue = fromVal + adjVal;
        } else if (targetField === 'fromAmount') {
          const toVal = field1 === 'toAmount' ? val1 : val2;
          const adjVal = field1 === 'adjustment' ? val1 : val2;
          computedValue = toVal - adjVal;
        }

        if (computedValue >= 0) {
          newFormData[targetField] = parseFloat(computedValue.toFixed(4)).toString();
        }
      }
    }

    setFormData(newFormData);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (formData.type === 'TRANSFER') {
      const fromVal = parseFloat(formData.fromAmount);
      const toVal = parseFloat(formData.toAmount);
      const adjVal = parseFloat(formData.adjustment);

      let validCount = 0;
      if (!isNaN(fromVal) && fromVal > 0) validCount++;
      if (!isNaN(toVal) && toVal > 0) validCount++;
      if (!isNaN(adjVal) && adjVal >= 0) validCount++;

      if (validCount < 2) {
        alert('At least two of From Amount, To Amount, or Adjustment must be valid positive numbers');
        return;
      }

      if (!formData.toAccountId) {
        alert('Please select a destination account');
        return;
      }

      const payload: any = {
        type: 'TRANSFER',
        transactionDate: formData.transactionDate.includes('T')
          ? formData.transactionDate
          : `${formData.transactionDate}T00:00:00Z`,
        fromAccountId: formData.accountId,
        toAccountId: formData.toAccountId,
        categoryId: formData.categoryId || null,
        labelIds: formData.labelIds || [],
        description: formData.description
      };

      const fieldsToSend = lastEdited.length === 2 ? lastEdited : (['fromAmount', 'adjustment'] as const);
      fieldsToSend.forEach(field => {
        payload[field] = parseFloat(formData[field]);
      });

      await onSubmit(payload);
    } else {
      const amountNum = parseFloat(formData.amount);
      if (isNaN(amountNum) || amountNum <= 0) {
        alert('Amount must be greater than zero');
        return;
      }

      const payload: any = {
        ...formData,
        amount: amountNum,
        labelIds: undefined,
        labels: formData.labelIds.map(id => ({ id })),
        transactionDate: formData.transactionDate.includes('T')
          ? formData.transactionDate
          : `${formData.transactionDate}T00:00:00Z`
      };

      await onSubmit(payload);
    }
  };

  const showCategory = true;
  const showToAccount = formData.type === 'TRANSFER';

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-1 col-span-2 md:col-span-1">
          <label htmlFor="trans-type" className="text-sm font-medium">Type</label>
          <select
            id="trans-type"
            disabled={isEditMode && (initialData?.kind === 'TRANSFER' || initialData?.type === 'TRANSFER')}
            value={formData.type}
            onChange={(e) => setFormData({ ...formData, type: e.target.value as any })}
            className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none disabled:opacity-50"
          >
            <option value="EXPENSE">Expense</option>
            <option value="INCOME">Income</option>
            {(!isEditMode || initialData?.kind === 'TRANSFER' || initialData?.type === 'TRANSFER') && (
              <option value="TRANSFER">Transfer</option>
            )}
            <option value="LEND">Lend</option>
            <option value="BORROW">Borrow</option>
          </select>
        </div>
        {formData.type !== 'TRANSFER' && (
          <div className="space-y-1 col-span-2 md:col-span-1">
            <label htmlFor="trans-amount" className="text-sm font-medium">Amount</label>
            <input
              id="trans-amount"
              required
              type="text"
              inputMode="decimal"
              value={formData.amount}
              onChange={(e) => {
                const val = e.target.value;
                if (val === '' || /^\d*\.?\d*$/.test(val)) {
                  setFormData({ ...formData, amount: val });
                }
              }}
              placeholder="123"
              className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
            />
          </div>
        )}
      </div>

      {formData.type === 'TRANSFER' && (
        <div className="grid grid-cols-3 gap-3 animate-in slide-in-from-top-1 duration-200">
          <div className="space-y-1">
            <label htmlFor="trans-from-amount" className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">From Amount</label>
            <input
              id="trans-from-amount"
              type="text"
              inputMode="decimal"
              value={formData.fromAmount}
              onChange={(e) => handleAmountChange('fromAmount', e.target.value)}
              placeholder="0.00"
              className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
            />
          </div>
          <div className="space-y-1">
            <label htmlFor="trans-to-amount" className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">To Amount</label>
            <input
              id="trans-to-amount"
              type="text"
              inputMode="decimal"
              value={formData.toAmount}
              onChange={(e) => handleAmountChange('toAmount', e.target.value)}
              placeholder="0.00"
              className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
            />
          </div>
          <div className="space-y-1">
            <label htmlFor="trans-adjustment" className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Adjustment</label>
            <input
              id="trans-adjustment"
              type="text"
              inputMode="decimal"
              value={formData.adjustment}
              onChange={(e) => handleAmountChange('adjustment', e.target.value)}
              placeholder="0.00"
              className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
            />
          </div>
        </div>
      )}

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
          <label htmlFor="trans-account" className="text-sm font-medium">
            {formData.type === 'TRANSFER' ? 'From Account' : 'Account'}
          </label>
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
        <label className="text-sm font-medium">Label</label>
        <MultiSelect
          options={labels.map(l => ({ id: l.id, name: l.name }))}
          selectedIds={formData.labelIds}
          onChange={(selected) => setFormData({ ...formData, labelIds: selected })}
        />
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
          {isLoading ? 'Saving...' : (isEditMode ? 'Save Changes' : 'Add Transaction')}
        </button>
      </div>
    </form>
  );
};
