import React, { useState, useEffect } from 'react';
import { usePreferences } from '../context/PreferenceContext';
import apiClient from '../api/client';
import type { Account } from '../types/account';
import type { Category } from '../types/category';
import type { Label } from '../types/label';
import type { TransactionType } from '../types/transaction';

export const PreferenceManager: React.FC = () => {
  const { preferences, updatePreferences, isLoading: isSaving } = usePreferences();
  
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [labels, setLabels] = useState<Label[]>([]);
  const [isLoadingData, setIsLoadingData] = useState(true);
  
  const [formData, setFormData] = useState({
    defaultAccountId: '',
    defaultTransactionType: '' as TransactionType | '',
    defaultCategoryId: '',
    defaultLabelId: '',
    currencySymbol: '₹',
    autoBackupEnabled: false,
    autoBackupFrequency: 'DAILY',
    autoBackupFormat: 'SQL'
  });

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [accRes, catRes, lblRes] = await Promise.all([
          apiClient.get<Account[]>('/accounts'),
          apiClient.get<Category[]>('/categories'),
          apiClient.get<Label[]>('/labels')
        ]);
        setAccounts(accRes.data);
        setCategories(catRes.data);
        setLabels(lblRes.data);
      } catch (error) {
        console.error('Failed to fetch data for preferences', error);
      } finally {
        setIsLoadingData(false);
      }
    };
    fetchData();
  }, []);

  useEffect(() => {
    if (preferences) {
      setFormData({
        defaultAccountId: preferences.defaultAccountId || '',
        defaultTransactionType: preferences.defaultTransactionType || '',
        defaultCategoryId: preferences.defaultCategoryId || '',
        defaultLabelId: preferences.defaultLabelId || '',
        currencySymbol: preferences.currencySymbol || '₹',
        autoBackupEnabled: preferences.autoBackupEnabled || false,
        autoBackupFrequency: preferences.autoBackupFrequency || 'DAILY',
        autoBackupFormat: preferences.autoBackupFormat || 'SQL'
      });
    }
  }, [preferences]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await updatePreferences({
      defaultAccountId: formData.defaultAccountId || null,
      defaultTransactionType: (formData.defaultTransactionType as TransactionType) || null,
      defaultCategoryId: formData.defaultCategoryId || null,
      defaultLabelId: formData.defaultLabelId || null,
      currencySymbol: formData.currencySymbol,
      autoBackupEnabled: formData.autoBackupEnabled,
      autoBackupFrequency: formData.autoBackupFrequency,
      autoBackupFormat: formData.autoBackupFormat
    });
  };

  if (isLoadingData) {
    return <div className="animate-pulse space-y-4">
      {[1, 2, 3, 4].map(i => <div key={i} className="h-10 bg-secondary rounded"></div>)}
    </div>;
  }

  return (
    <div className="bg-card border border-border rounded-xl p-6">
      <h3 className="text-lg font-bold mb-1">Transaction Defaults</h3>
      <p className="text-sm text-muted-foreground mb-6">
        Pre-populate the transaction form with these values to save time.
      </p>

      <form onSubmit={handleSubmit} className="space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="space-y-2">
            <label htmlFor="pref-account" className="text-sm font-semibold">Default Account</label>
            <select
              id="pref-account"
              value={formData.defaultAccountId}
              onChange={e => setFormData({ ...formData, defaultAccountId: e.target.value })}
              className="w-full border border-input bg-background p-2 rounded-md outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="">None (No default)</option>
              {accounts.map(acc => (
                <option key={acc.id} value={acc.id}>{acc.name}</option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <label htmlFor="pref-type" className="text-sm font-semibold">Default Type</label>
            <select
              id="pref-type"
              value={formData.defaultTransactionType}
              onChange={e => setFormData({ ...formData, defaultTransactionType: e.target.value as TransactionType })}
              className="w-full border border-input bg-background p-2 rounded-md outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="">None (No default)</option>
              <option value="EXPENSE">Expense</option>
              <option value="INCOME">Income</option>
              <option value="TRANSFER">Transfer</option>
              <option value="LEND">Lend</option>
              <option value="BORROW">Borrow</option>
            </select>
          </div>

          <div className="space-y-2">
            <label htmlFor="pref-category" className="text-sm font-semibold">Default Category</label>
            <select
              id="pref-category"
              value={formData.defaultCategoryId}
              onChange={e => setFormData({ ...formData, defaultCategoryId: e.target.value })}
              className="w-full border border-input bg-background p-2 rounded-md outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="">None (No default)</option>
              {categories.map(cat => (
                <option key={cat.id} value={cat.id}>{cat.icon} {cat.name}</option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <label htmlFor="pref-label" className="text-sm font-semibold">Default Label</label>
            <select
              id="pref-label"
              value={formData.defaultLabelId}
              onChange={e => setFormData({ ...formData, defaultLabelId: e.target.value })}
              className="w-full border border-input bg-background p-2 rounded-md outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="">None (No default)</option>
              {labels.map(lbl => (
                <option key={lbl.id} value={lbl.id}>{lbl.name}</option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <label htmlFor="pref-currency" className="text-sm font-semibold">Currency Symbol</label>
            <input
              id="pref-currency"
              type="text"
              value={formData.currencySymbol}
              onChange={e => setFormData({ ...formData, currencySymbol: e.target.value })}
              placeholder="e.g. ₹, $, €"
              className="w-full border border-input bg-background p-2 rounded-md outline-none focus:ring-2 focus:ring-ring"
              maxLength={5}
            />
          </div>
        </div>

        <div className="border-t border-border pt-6">
          <h3 className="text-lg font-bold mb-1">Automatic Backups</h3>
          <p className="text-sm text-muted-foreground mb-6">
            Schedule automatic data backups to the server.
          </p>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="flex items-center space-x-2">
              <input
                id="auto-backup"
                type="checkbox"
                checked={formData.autoBackupEnabled}
                onChange={e => setFormData({ ...formData, autoBackupEnabled: e.target.checked })}
                className="w-4 h-4 rounded border-gray-300 text-primary focus:ring-primary"
              />
              <label htmlFor="auto-backup" className="text-sm font-semibold">Enable Automatic Backups</label>
            </div>

            {formData.autoBackupEnabled && (
              <>
                <div className="space-y-2">
                  <label htmlFor="backup-frequency" className="text-sm font-semibold">Frequency</label>
                  <select
                    id="backup-frequency"
                    value={formData.autoBackupFrequency}
                    onChange={e => setFormData({ ...formData, autoBackupFrequency: e.target.value })}
                    className="w-full border border-input bg-background p-2 rounded-md outline-none focus:ring-2 focus:ring-ring"
                  >
                    <option value="DAILY">Daily</option>
                    <option value="WEEKLY">Weekly</option>
                    <option value="MONTHLY">Monthly</option>
                  </select>
                </div>

                <div className="space-y-2">
                  <label htmlFor="backup-format" className="text-sm font-semibold">Format</label>
                  <select
                    id="backup-format"
                    value={formData.autoBackupFormat}
                    onChange={e => setFormData({ ...formData, autoBackupFormat: e.target.value })}
                    className="w-full border border-input bg-background p-2 rounded-md outline-none focus:ring-2 focus:ring-ring"
                  >
                    <option value="SQL">SQL (Full Restore)</option>
                    <option value="CSV">CSV (Transaction Export)</option>
                    <option value="BOTH">Both</option>
                  </select>
                </div>
              </>
            )}
          </div>
        </div>

        <div className="pt-4 flex justify-end">
          <button
            type="submit"
            disabled={isSaving}
            className="bg-primary text-primary-foreground px-6 py-2 rounded-md font-bold hover:opacity-90 transition-opacity disabled:opacity-50"
          >
            {isSaving ? 'Saving...' : 'Save Preferences'}
          </button>
        </div>
      </form>
    </div>
  );
};
