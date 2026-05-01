import { useEffect, useState } from 'react';
import { ThemeToggle } from '../components/ThemeToggle';
import { useAuth } from '../context/AuthContext';
import { BalanceCard } from '../components/BalanceCard';
import { Modal } from '../components/Modal';
import { AccountForm } from '../components/AccountForm';
import type { Account, AccountType, CreateAccountRequest } from '../types/account';
import apiClient from '../api/client';
import { useNavigate } from 'react-router-dom';

export const Dashboard = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const fetchAccounts = async () => {
    try {
      const response = await apiClient.get('/accounts');
      setAccounts(response.data);
    } catch (error) {
      console.error('Failed to fetch accounts', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAccounts();
  }, []);

  const handleCreateAccount = async (data: CreateAccountRequest) => {
    setIsSubmitting(true);
    try {
      await apiClient.post('/accounts', data);
      await fetchAccounts();
      setIsModalOpen(false);
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
              <path strokeLinecap="round" strokeLinejoin="round" d="M10.34 15.84c-.688-.06-1.386-.09-2.09-.09H7.5a4.5 4.5 0 110-9h.75c.704 0 1.402-.03 2.09-.09m0 9.18c.253.062.506.123.76.183L15 18.24V5.76l-3.9-.877c-.254.06-.507.121-.76.183m0 9.18V4.938m0 9.18c-.253.062-.506.123-.76.183L4.5 13.5v-3l1.18-.263c.253.062.506.123.76.183m0 0V4.938" />
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
        <div className="max-w-6xl mx-auto">
          <h2 className="text-3xl font-bold mb-6">Dashboard</h2>
          <section className="mb-10">
            <div className="flex justify-between items-end mb-6">
              <div>
                <h2 className="text-sm font-medium text-muted-foreground uppercase tracking-wider">Total Net Worth</h2>
                <p className={`text-4xl font-black mt-1 ${totalBalance >= 0 ? 'text-foreground' : 'text-red-500'}`}>
                  {formatCurrency(totalBalance)}
                </p>
              </div>
              <button
                onClick={() => setIsModalOpen(true)}
                className="bg-primary text-primary-foreground px-4 py-2 rounded-md font-medium hover:opacity-90 transition-opacity"
              >
                + Add Account
              </button>
            </div>

            {loading ? (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 animate-pulse">
                {[1, 2, 3].map(i => (
                  <div key={i} className="h-32 bg-secondary rounded-lg"></div>
                ))}
              </div>
            ) : accounts.length === 0 ? (
              <div className="text-center py-20 bg-card rounded-xl border-2 border-dashed border-border">
                <p className="text-muted-foreground">No accounts found. Create your first account to get started!</p>
                <button
                  onClick={() => setIsModalOpen(true)}
                  className="mt-4 text-primary font-semibold hover:underline"
                >
                  Create your first account
                </button>
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
        </div>
      </main>

      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title="Create New Account"
      >
        <AccountForm
          onSubmit={handleCreateAccount}
          onCancel={() => setIsModalOpen(false)}
          isLoading={isSubmitting}
        />
      </Modal>
    </div>
  );
};
