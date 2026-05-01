import { useEffect, useState } from 'react';
import { ThemeToggle } from '../components/ThemeToggle';
import { useAuth } from '../context/AuthContext';
import { BalanceCard } from '../components/BalanceCard';
import { Modal } from '../components/Modal';
import { AccountForm } from '../components/AccountForm';
import { TransactionList } from '../components/TransactionList';
import { TransactionForm } from '../components/TransactionForm';
import type { Account, AccountType, CreateAccountRequest } from '../types/account';
import type { Category } from '../types/category';
import type { Label } from '../types/label';
import type { Transaction, CreateTransactionRequest } from '../types/transaction';
import apiClient from '../api/client';
import { useNavigate } from 'react-router-dom';

export const Dashboard = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [labels, setLabels] = useState<Label[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  
  const [loadingAccounts, setLoadingAccounts] = useState(true);
  const [loadingTransactions, setLoadingTransactions] = useState(true);
  
  const [isAccountModalOpen, setIsAccountModalOpen] = useState(false);
  const [isTransactionModalOpen, setIsTransactionModalOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const fetchData = async () => {
    try {
      const [accRes, catRes, lblRes, transRes] = await Promise.all([
        apiClient.get('/accounts'),
        apiClient.get('/categories'),
        apiClient.get('/labels'),
        apiClient.get('/transactions')
      ]);
      setAccounts(accRes.data);
      setCategories(catRes.data);
      setLabels(lblRes.data);
      setTransactions(transRes.data);
    } catch (error) {
      console.error('Failed to fetch dashboard data', error);
    } finally {
      setLoadingAccounts(false);
      setLoadingTransactions(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleCreateAccount = async (data: CreateAccountRequest) => {
    setIsSubmitting(true);
    try {
      await apiClient.post('/accounts', data);
      await fetchData();
      setIsAccountModalOpen(false);
    } catch (error) {
      console.error('Failed to create account', error);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCreateTransaction = async (data: CreateTransactionRequest) => {
    setIsSubmitting(true);
    try {
      if (data.type === 'TRANSFER') {
        // TransferRequest expects fromAccountId, toAccountId directly
        const transferPayload = {
          fromAccountId: data.accountId,
          toAccountId: data.toAccountId,
          amount: data.amount,
          description: data.description || '',
          transactionDate: data.transactionDate,
          categoryId: data.categoryId
        };
        await apiClient.post('/transactions/transfer', transferPayload);
      } else {
        // Regular Transaction expects { account: { id: ... }, category: { id: ... }, label: { id: ... } }
        const transactionPayload = {
          amount: data.amount,
          type: data.type,
          description: data.description,
          transactionDate: data.transactionDate,
          account: { id: data.accountId },
          category: data.categoryId ? { id: data.categoryId } : null,
          label: data.labelId ? { id: data.labelId } : null
        };
        await apiClient.post('/transactions', transactionPayload);
      }
      await fetchData();
      setIsTransactionModalOpen(false);
    } catch (error) {
      console.error('Failed to create transaction', error);
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
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(value);
  };

  return (
    <div className="min-h-screen bg-background text-foreground transition-colors">
      <header className="border-b border-border p-4 flex justify-between items-center bg-card sticky top-0 z-10">
        <h1 className="text-xl font-bold">Budget Tracker</h1>
        <div className="flex items-center gap-4">
          <span className="text-sm text-muted-foreground hidden sm:inline">{user?.email}</span>
          <ThemeToggle />
          <button
            onClick={() => navigate('/settings')}
            className="p-2 hover:bg-secondary rounded-full transition-colors"
            aria-label="Settings"
          >
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-5 h-5">
              <path strokeLinecap="round" strokeLinejoin="round" d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.592c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.324.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 011.37.49l1.296 2.247a1.125 1.125 0 01-.26 1.431l-1.003.827c-.293.24-.438.613-.431.992a6.759 6.759 0 010 .255c-.007.378.138.75.43.99l1.005.828c.424.35.534.954.26 1.43l-1.298 2.247a1.125 1.125 0 01-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.57 6.57 0 01-.22.128c-.331.183-.581.495-.644.869l-.213 1.28c-.09.543-.56.941-1.11.941h-2.594c-.55 0-1.02-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.57 0 01-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 01-1.369-.49l-1.297-2.247a1.125 1.125 0 01.26-1.431l1.004-.827c.292-.24.437-.613.43-.992a6.932 6.932 0 010-.255c.007-.378-.138-.75-.43-.99l-1.004-.828a1.125 1.125 0 01-.26-1.43l1.297-2.247a1.125 1.125 0 011.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.087.22-.128.332-.183.582-.495.644-.869l.214-1.281z" />
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          </button>
          <button
            onClick={logout}
            className="text-sm bg-destructive text-destructive-foreground hover:bg-destructive/90 px-3 py-1 rounded-md transition-colors"
          >
            Logout
          </button>
        </div>
      </header>

      <main className="p-4 sm:p-8">
        <div className="max-w-6xl mx-auto space-y-12">
          {/* Accounts Section */}
          <section>
            <div className="flex justify-between items-end mb-6">
              <div>
                <h2 className="text-3xl font-bold">Accounts</h2>
                <div className="mt-1">
                  <span className="text-sm font-medium text-muted-foreground uppercase tracking-wider">Net Worth: </span>
                  <span className={`text-xl font-black ${totalBalance >= 0 ? 'text-foreground' : 'text-red-500'}`}>
                    {formatCurrency(totalBalance)}
                  </span>
                </div>
              </div>
              <button
                onClick={() => setIsAccountModalOpen(true)}
                className="bg-secondary text-secondary-foreground px-4 py-2 rounded-md font-medium hover:bg-secondary/80 transition-colors"
              >
                + Add Account
              </button>
            </div>

            {loadingAccounts ? (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 animate-pulse">
                {[1, 2, 3].map(i => (
                  <div key={i} className="h-32 bg-secondary rounded-lg"></div>
                ))}
              </div>
            ) : accounts.length === 0 ? (
              <div className="text-center py-10 bg-card rounded-xl border-2 border-dashed border-border">
                <p className="text-muted-foreground">No accounts yet.</p>
              </div>
            ) : (
              <div className="space-y-8">
                {Object.entries(groupedAccounts).map(([type, accs]) => (
                  <div key={type}>
                    <h3 className="text-xs font-bold text-muted-foreground uppercase mb-4 tracking-widest flex items-center gap-2">
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

          {/* Transactions Section */}
          <section>
            <div className="flex justify-between items-center mb-6">
              <h2 className="text-3xl font-bold">Recent Transactions</h2>
              <button
                onClick={() => setIsTransactionModalOpen(true)}
                className="bg-primary text-primary-foreground px-4 py-2 rounded-md font-medium hover:opacity-90 transition-opacity"
              >
                + Add Transaction
              </button>
            </div>
            <TransactionList transactions={transactions} loading={loadingTransactions} />
          </section>
        </div>
      </main>

      {/* Modals */}
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

      <Modal
        isOpen={isTransactionModalOpen}
        onClose={() => setIsTransactionModalOpen(false)}
        title="Add Transaction"
      >
        <TransactionForm
          accounts={accounts}
          categories={categories}
          labels={labels}
          onSubmit={handleCreateTransaction}
          onCancel={() => setIsTransactionModalOpen(false)}
          isLoading={isSubmitting}
        />
      </Modal>
    </div>
  );
};
