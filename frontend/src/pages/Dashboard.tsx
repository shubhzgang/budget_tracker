import { useEffect, useState, useCallback } from 'react';
import { useUI } from '../context/UIContext';
import { usePreferences } from '../context/PreferenceContext';
import { BalanceCard } from '../components/BalanceCard';
import { Modal } from '../components/Modal';
import { AccountForm } from '../components/AccountForm';
import { TransactionList } from '../components/TransactionList';
import { Analytics } from '../components/Analytics';
import type { Account, AccountType, CreateAccountRequest } from '../types/account';
import type { Category } from '../types/category';
import type { Label } from '../types/label';
import type { Transaction } from '../types/transaction';
import type { PaginatedResponse } from '../types/pagination';
import apiClient from '../api/client';
import { Link } from 'react-router-dom';

export const Dashboard = () => {
  const { refreshTrigger, triggerRefresh } = useUI();
  const { preferences } = usePreferences();
  
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [, setCategories] = useState<Category[]>([]);
  const [, setLabels] = useState<Label[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [analyticsTransactions, setAnalyticsTransactions] = useState<Transaction[]>([]);
  
  const [loadingAccounts, setLoadingAccounts] = useState(true);
  const [loadingTransactions, setLoadingTransactions] = useState(true);
  const [loadingAnalytics, setLoadingAnalytics] = useState(true);
  
  const [isAccountModalOpen, setIsAccountModalOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const fetchData = useCallback(async () => {
    setLoadingAccounts(true);
    setLoadingTransactions(true);
    setLoadingAnalytics(true);
    try {
      const [accRes, catRes, lblRes, transRes, analyticsRes] = await Promise.all([
        apiClient.get('/accounts'),
        apiClient.get('/categories'),
        apiClient.get('/labels'),
        apiClient.get<PaginatedResponse<Transaction>>('/transactions?page=0&size=10&sort=transactionDate,desc'),
        apiClient.get<PaginatedResponse<Transaction>>('/transactions?page=0&size=1000&sort=transactionDate,desc')
      ]);
      setAccounts(accRes.data);
      setCategories(catRes.data);
      setLabels(lblRes.data);
      setTransactions(transRes.data.content);
      setAnalyticsTransactions(analyticsRes.data.content);
    } catch (error) {
      console.error('Failed to fetch dashboard data', error);
    } finally {
      setLoadingAccounts(false);
      setLoadingTransactions(false);
      setLoadingAnalytics(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData, refreshTrigger]);

  const handleCreateAccount = async (data: CreateAccountRequest) => {
    setIsSubmitting(true);
    try {
      await apiClient.post('/accounts', data);
      triggerRefresh();
      setIsAccountModalOpen(false);
    } catch (error) {
      console.error('Failed to create account', error);
    } finally {
      setIsSubmitting(false);
    }
  };

  const totalBalance = accounts.reduce((sum, acc) => {
    if (acc.type === 'CREDIT_CARD') return sum - acc.balance;
    return sum + acc.balance;
  }, 0);

  const groupedAccounts = accounts.reduce((groups, account) => {
    const type = account.type;
    if (!groups[type]) groups[type] = [];
    groups[type].push(account);
    return groups;
  }, {} as Record<AccountType, Account[]>);

  const formatCurrency = (value: number) => {
    const symbol = preferences?.currencySymbol || '₹';
    const formatted = new Intl.NumberFormat('en-IN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(Math.abs(value));
    return `${value < 0 ? '-' : ''}${symbol}${formatted}`;
  };

  return (
    <div className="space-y-12">
      {/* Accounts Section */}
      <section>
        <div className="flex justify-between items-end mb-6">
          <div>
            <h2 className="text-2xl md:text-3xl font-bold">Accounts</h2>
            <div className="mt-1">
              <span className="text-sm font-medium text-muted-foreground uppercase tracking-wider">Net Worth: </span>
              <span className={`text-xl font-black ${totalBalance >= 0 ? 'text-foreground' : 'text-red-500'}`}>
                {formatCurrency(totalBalance)}
              </span>
            </div>
          </div>
          <div className="flex gap-2">
            <button
              onClick={fetchData}
              className="p-2 text-muted-foreground hover:text-foreground transition-colors"
              title="Refresh Data"
            >
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className={`w-5 h-5 ${loadingAccounts ? 'animate-spin' : ''}`}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182m0-4.991v4.99" />
              </svg>
            </button>
            <button
              onClick={() => setIsAccountModalOpen(true)}
              className="bg-secondary text-secondary-foreground px-4 py-2 rounded-md font-medium hover:bg-secondary/80 transition-colors text-sm md:text-base"
            >
              + Add Account
            </button>
          </div>
        </div>

        {loadingAccounts ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 animate-pulse">
            {[1, 2, 3].map(i => (
              <div key={i} className="h-32 bg-secondary rounded-lg"></div>
            ))}
          </div>
        ) : accounts.length === 0 ? (
          <div className="text-center py-10 bg-card rounded-xl border-2 border-dashed border-border">
            <p className="text-muted-foreground mb-4">No accounts yet.</p>
            <button 
              onClick={fetchData}
              className="text-primary font-medium hover:underline text-sm"
            >
              Check for seeded data
            </button>
          </div>
        ) : (
          <div className="space-y-8">
            {Object.entries(groupedAccounts).map(([type, accs]) => (
              <div key={type}>
                <h3 className="text-[10px] font-bold text-muted-foreground uppercase mb-4 tracking-widest flex items-center gap-2">
                  {type.replace('_', ' ')}
                  <span className="h-[1px] bg-border flex-grow"></span>
                </h3>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                  {accs.map(account => (
                    <BalanceCard key={account.id} account={account} />
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Analytics Section */}
      <section>
        <h2 className="text-2xl md:text-3xl font-bold mb-6">Spending Insights</h2>
        <Analytics transactions={analyticsTransactions} loading={loadingAnalytics} />
      </section>

      {/* Transactions Section */}
      <section>
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-2xl md:text-3xl font-bold">Recent Transactions</h2>
          <Link
            to="/transactions"
            className="text-primary hover:underline font-medium text-sm md:text-base"
          >
            View All
          </Link>
        </div>
        <TransactionList transactions={transactions} loading={loadingTransactions} />
      </section>

      {/* Account Modal */}
      <Modal
        isOpen={isAccountModalOpen}
        onClose={() => setIsAccountModalOpen(false)}
        title="Create New Account"
      >
        <AccountForm
          onSubmit={handleCreateAccount}
          onCancel={() => setIsAccountModalOpen(false)}
          isLoading={isSubmitting}
        />
      </Modal>
    </div>
  );
};
