import React, { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useUI } from '../context/UIContext';
import { ThemeToggle } from './ThemeToggle';
import { Modal } from './Modal';
import { TransactionForm } from './TransactionForm';
import apiClient from '../api/client';
import type { Account } from '../types/account';
import type { Category } from '../types/category';
import type { Label } from '../types/label';
import type { CreateTransactionRequest } from '../types/transaction';

export const Layout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user, logout } = useAuth();
  const { isTransactionModalOpen, openTransactionModal, closeTransactionModal, refreshTrigger, triggerRefresh } = useUI();
  const location = useLocation();

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [labels, setLabels] = useState<Label[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [accRes, catRes, lblRes] = await Promise.all([
          apiClient.get('/accounts'),
          apiClient.get('/categories'),
          apiClient.get('/labels'),
        ]);
        setAccounts(accRes.data);
        setCategories(catRes.data);
        setLabels(lblRes.data);
      } catch (error) {
        console.error('Failed to fetch modal dependencies', error);
      }
    };
    if (user) {
      fetchData();
    }
  }, [user, refreshTrigger]);

  const handleCreateTransaction = async (data: CreateTransactionRequest) => {
    setIsSubmitting(true);
    try {
      if (data.type === 'TRANSFER') {
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
      closeTransactionModal();
      triggerRefresh();
    } catch (error) {
      console.error('Failed to create transaction', error);
    } finally {
      setIsSubmitting(false);
    }
  };

  const isActive = (path: string) => location.pathname === path;

  const navLinks = [
    { name: 'Dashboard', path: '/dashboard', icon: (
      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6">
        <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 12l8.954-8.955c.44-.439 1.152-.439 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25" />
      </svg>
    )},
    { name: 'Transactions', path: '/transactions', icon: (
      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6">
        <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 6.75h12M8.25 12h12m-12 5.25h12M3.75 6.75h.007v.008H3.75V6.75zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zM3.75 12h.007v.008H3.75V12zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm-.375 5.25h.007v.008H3.75v-.008zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0z" />
      </svg>
    )},
    { name: 'Settings', path: '/settings', icon: (
      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6">
        <path strokeLinecap="round" strokeLinejoin="round" d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.324.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 011.37.49l1.296 2.247a1.125 1.125 0 01-.26 1.431l-1.003.827c-.293.24-.438.613-.431.992a6.759 6.759 0 010 .255c-.007.378.138.75.43.99l1.005.828c.424.35.534.954.26 1.43l-1.298 2.247a1.125 1.125 0 01-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.57 6.57 0 01-.22.128c-.331.183-.581.495-.644.869l-.213 1.28c-.09.543-.56.941-1.11.941h-2.594c-.55 0-1.02-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 01-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 01-1.369-.49l-1.297-2.247a1.125 1.125 0 01.26-1.431l1.004-.827c.292-.24.437-.613.43-.992a6.932 6.932 0 010-.255c.007-.378-.138-.75-.43-.99l-1.004-.828a1.125 1.125 0 01-.26-1.43l1.297-2.247a1.125 1.125 0 011.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.087.22-.128.332-.183.582-.495.644-.869l.214-1.281z" />
        <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
      </svg>
    )},
  ];

  return (
    <div className="min-h-screen bg-background text-foreground transition-colors pb-20 md:pb-0">
      {/* Desktop Top Nav */}
      <header className="hidden md:flex border-b border-border p-4 justify-between items-center bg-card sticky top-0 z-50">
        <div className="flex items-center gap-8">
          <Link to="/dashboard" className="text-xl font-bold text-primary">BudgetTracker</Link>
          <nav className="flex gap-6">
            {navLinks.map((link) => (
              <Link
                key={link.path}
                to={link.path}
                className={`text-sm font-medium transition-colors hover:text-primary ${
                  isActive(link.path) ? 'text-primary border-b-2 border-primary' : 'text-muted-foreground'
                }`}
              >
                {link.name}
              </Link>
            ))}
          </nav>
        </div>
        <div className="flex items-center gap-4">
          <ThemeToggle />
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium">{user?.email}</span>
            <button
              onClick={logout}
              className="text-xs bg-destructive text-destructive-foreground hover:bg-destructive/90 px-3 py-1 rounded-md transition-colors"
            >
              Logout
            </button>
          </div>
        </div>
      </header>

      {/* Mobile Top Header (Minimal) */}
      <header className="md:hidden border-b border-border p-4 flex justify-between items-center bg-card sticky top-0 z-50">
        <h1 className="text-lg font-bold text-primary">BudgetTracker</h1>
        <ThemeToggle />
      </header>

      {/* Main Content */}
      <main className="container mx-auto px-4 pt-6 pb-32 md:pt-8 md:pb-24">
        {children}
      </main>

      {/* Mobile Bottom Nav Bar */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 border-t border-border bg-card/80 backdrop-blur-md flex justify-around items-center p-3 z-50">
        <Link to="/transactions" className={`flex flex-col items-center gap-1 ${isActive('/transactions') ? 'text-primary' : 'text-muted-foreground'}`}>
          {navLinks[1].icon}
          <span className="text-[10px] font-bold uppercase tracking-tighter">Transactions</span>
        </Link>
        <Link to="/dashboard" className={`flex flex-col items-center gap-1 ${isActive('/dashboard') ? 'text-primary' : 'text-muted-foreground'}`}>
          {navLinks[0].icon}
          <span className="text-[10px] font-bold uppercase tracking-tighter">Dashboard</span>
        </Link>
        <Link to="/settings" className={`flex flex-col items-center gap-1 ${isActive('/settings') ? 'text-primary' : 'text-muted-foreground'}`}>
          {navLinks[2].icon}
          <span className="text-[10px] font-bold uppercase tracking-tighter">Settings</span>
        </Link>
      </nav>

      {/* Universal Floating Action Button */}
      <button
        onClick={openTransactionModal}
        className="fixed bottom-24 right-6 md:bottom-8 md:right-8 w-14 h-14 bg-primary text-primary-foreground rounded-full shadow-lg flex items-center justify-center hover:scale-110 active:scale-95 transition-all z-40"
        aria-label="Add Transaction"
      >
        <span className="sr-only">Add Transaction</span>
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" className="w-8 h-8">
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
        </svg>
      </button>

      {/* Shared Modal */}
      <Modal
        isOpen={isTransactionModalOpen}
        onClose={closeTransactionModal}
        title="Add Transaction"
      >
        <TransactionForm
          accounts={accounts}
          categories={categories}
          labels={labels}
          onSubmit={handleCreateTransaction}
          onCancel={closeTransactionModal}
          isLoading={isSubmitting}
        />
      </Modal>
    </div>
  );
};
