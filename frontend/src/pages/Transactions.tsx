import { useEffect, useState, useCallback } from 'react';
import { useUI } from '../context/UIContext';
import { TransactionList } from '../components/TransactionList';
import type { Transaction, TransactionType } from '../types/transaction';
import type { PaginatedResponse } from '../types/pagination';
import apiClient from '../api/client';

export const Transactions = () => {
  const { refreshTrigger } = useUI();

  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);

  const [filters, setFilters] = useState({
    search: '',
    type: '' as TransactionType | '',
    startDate: '',
    endDate: '',
  });

  const fetchTransactions = useCallback(async (pageNum: number, currentFilters: typeof filters, isLoadMore = false) => {
    if (isLoadMore) setLoadingMore(true);
    else setLoading(true);

    try {
      const params = new URLSearchParams();
      params.append('page', pageNum.toString());
      params.append('size', '20');
      params.append('sort', 'transactionDate,desc');

      if (currentFilters.search) params.append('search', currentFilters.search);
      if (currentFilters.type) params.append('type', currentFilters.type);
      if (currentFilters.startDate) params.append('startDate', `${currentFilters.startDate}T00:00:00Z`);
      if (currentFilters.endDate) params.append('endDate', `${currentFilters.endDate}T23:59:59Z`);

      const res = await apiClient.get<PaginatedResponse<Transaction>>(`/transactions?${params.toString()}`);
      
      if (isLoadMore) {
        setTransactions(prev => [...prev, ...res.data.content]);
      } else {
        setTransactions(res.data.content);
      }
      
      setHasMore(!res.data.last);
    } catch (error) {
      console.error('Failed to fetch transactions', error);
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }, []);

  useEffect(() => {
    setPage(0);
    fetchTransactions(0, filters);
  }, [filters, fetchTransactions, refreshTrigger]);

  const handleLoadMore = () => {
    const nextPage = page + 1;
    setPage(nextPage);
    fetchTransactions(nextPage, filters, true);
  };

  const handleFilterChange = (key: keyof typeof filters, value: string) => {
    setFilters(prev => ({ ...prev, [key]: value }));
  };

  return (
    <div className="space-y-8">
      <h2 className="text-2xl md:text-3xl font-bold">All Transactions</h2>
      
      {/* Filters */}
      <section className="bg-card p-4 rounded-xl border border-border space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="space-y-1">
            <label className="text-[10px] font-bold uppercase text-muted-foreground tracking-widest">Search</label>
            <input
              type="text"
              placeholder="Description, category..."
              value={filters.search}
              onChange={(e) => handleFilterChange('search', e.target.value)}
              className="w-full bg-background border border-input p-2 rounded-md text-sm outline-none focus:ring-2 focus:ring-ring transition-all"
            />
          </div>
          <div className="space-y-1">
            <label className="text-[10px] font-bold uppercase text-muted-foreground tracking-widest">Type</label>
            <select
              value={filters.type}
              onChange={(e) => handleFilterChange('type', e.target.value)}
              className="w-full bg-background border border-input p-2 rounded-md text-sm outline-none focus:ring-2 focus:ring-ring transition-all"
            >
              <option value="">All Types</option>
              <option value="EXPENSE">Expense</option>
              <option value="INCOME">Income</option>
              <option value="TRANSFER">Transfer</option>
              <option value="LEND">Lend</option>
              <option value="BORROW">Borrow</option>
            </select>
          </div>
          <div className="space-y-1">
            <label className="text-[10px] font-bold uppercase text-muted-foreground tracking-widest">From Date</label>
            <input
              type="date"
              value={filters.startDate}
              onChange={(e) => handleFilterChange('startDate', e.target.value)}
              className="w-full bg-background border border-input p-2 rounded-md text-sm outline-none focus:ring-2 focus:ring-ring transition-all"
            />
          </div>
          <div className="space-y-1">
            <label className="text-[10px] font-bold uppercase text-muted-foreground tracking-widest">To Date</label>
            <input
              type="date"
              value={filters.endDate}
              onChange={(e) => handleFilterChange('endDate', e.target.value)}
              className="w-full bg-background border border-input p-2 rounded-md text-sm outline-none focus:ring-2 focus:ring-ring transition-all"
            />
          </div>
        </div>
      </section>

      {/* Transactions List */}
      <section className="space-y-6">
        <TransactionList transactions={transactions} loading={loading && page === 0} />
        
        {hasMore && (
          <div className="flex justify-center pt-4">
            <button
              onClick={handleLoadMore}
              disabled={loadingMore}
              className="px-6 py-2 bg-secondary text-secondary-foreground rounded-md font-medium hover:bg-secondary/80 transition-colors disabled:opacity-50"
            >
              {loadingMore ? 'Loading...' : 'Load More'}
            </button>
          </div>
        )}

        {!hasMore && transactions.length > 0 && (
          <p className="text-center text-sm text-muted-foreground">No more transactions to show.</p>
        )}
      </section>
    </div>
  );
};
