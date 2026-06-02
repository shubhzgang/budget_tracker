import React from 'react';
import { usePreferences } from '../context/PreferenceContext';
import type { ActivityItem } from '../types/activity';

interface TransactionListProps {
  transactions: ActivityItem[];
  loading?: boolean;
  onEdit?: (item: ActivityItem) => void;
  onDelete?: (item: ActivityItem) => void;
}

export const TransactionList: React.FC<TransactionListProps> = ({ transactions, loading, onEdit, onDelete }) => {
  const { preferences } = usePreferences();
  
  const formatCurrency = (value: number) => {
    const symbol = preferences?.currencySymbol || '₹';
    const formatted = new Intl.NumberFormat('en-IN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(Math.abs(value));
    return `${value < 0 ? '-' : ''}${symbol}${formatted}`;
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  };

  if (loading) {
    return (
      <div className="space-y-4 animate-pulse">
        {[1, 2, 3].map((i) => (
          <div key={i} className="h-16 bg-secondary rounded-lg"></div>
        ))}
      </div>
    );
  }

  if (transactions.length === 0) {
    return (
      <div className="text-center py-10 bg-card rounded-lg border border-dashed border-border">
        <p className="text-muted-foreground text-sm">No transactions yet.</p>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {transactions.map((item) => {
        const isTransfer = item.kind === 'TRANSFER' || item.type === 'TRANSFER';
        
        return (
          <div
            key={item.id}
            className="group flex items-center justify-between p-4 bg-card rounded-lg border border-border hover:shadow-sm transition-shadow"
          >
            <div className="flex items-center gap-4">
              <div className="w-10 h-10 rounded-full bg-secondary flex items-center justify-center text-xl">
                {item.category?.icon || (isTransfer ? '🔄' : '💸')}
              </div>
              <div>
                <p className="font-semibold text-sm">
                  {isTransfer
                    ? item.description || 'Transfer'
                    : item.description || item.category?.name || 'No description'}
                </p>
                <div className="flex items-center gap-2 mt-0.5">
                  <span className="text-xs text-muted-foreground">{formatDate(item.transactionDate)}</span>
                  <span className="text-[10px] text-muted-foreground uppercase font-bold">•</span>
                  <span className="text-xs text-muted-foreground">
                    {item.account?.name}
                    {isTransfer && item.toAccount
                      ? ` → ${item.toAccount.name}`
                      : ''}
                  </span>
                  {item.labels && item.labels.length > 0 && item.labels.map((label) => (
                    <React.Fragment key={label.id}>
                      <span className="text-[10px] text-muted-foreground uppercase font-bold">•</span>
                      <span className="px-1.5 py-0.5 bg-accent text-accent-foreground text-[10px] rounded-full font-medium">
                        {label.name}
                      </span>
                    </React.Fragment>
                  ))}
                </div>
              </div>
            </div>
            <div className="flex items-center gap-3">
              {onEdit && (
                <button
                  onClick={() => onEdit(item)}
                  className="p-1.5 rounded-md text-muted-foreground hover:text-primary hover:bg-primary/10 transition-colors opacity-100 sm:opacity-0 sm:group-hover:opacity-100"
                  aria-label={`Edit ${item.description || 'transaction'}`}
                  title="Edit"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-4 h-4">
                    <path strokeLinecap="round" strokeLinejoin="round" d="m16.862 4.487 1.687-1.688a1.875 1.875 0 1 1 2.652 2.652L6.83 21.75a4.5 4.5 0 0 1-1.897 1.13l-2.685.8.8-2.685a4.5 4.5 0 0 1 1.13-1.897L16.863 4.487Zm0 0L19.5 7.125" />
                  </svg>
                </button>
              )}
              {onDelete && (
                <button
                  onClick={() => onDelete(item)}
                  className="p-1.5 rounded-md text-muted-foreground hover:text-red-500 hover:bg-red-500/10 transition-colors opacity-100 sm:opacity-0 sm:group-hover:opacity-100"
                  aria-label={`Delete ${item.description || 'transaction'}`}
                  title="Delete"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-4 h-4">
                    <path strokeLinecap="round" strokeLinejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
                  </svg>
                </button>
              )}
              <div className="text-right">
                <p
                  className={`font-bold ${
                    item.type === 'INCOME' || item.type === 'BORROW'
                      ? 'text-green-500'
                      : isTransfer
                      ? 'text-foreground'
                      : 'text-red-500'
                  }`}
                >
                  {item.type === 'INCOME' || item.type === 'BORROW' ? '+' : '-'}
                  {formatCurrency(Math.abs(isTransfer ? (item.fromAmount || 0) : (item.amount || 0)))}
                </p>
                
                {isTransfer && item.adjustment !== undefined && item.adjustment > 0 ? (
                  <div className="flex items-center justify-end gap-1 mt-0.5">
                    <span className="px-1.5 py-0.5 bg-green-500/10 text-green-500 text-[9px] rounded-full font-bold uppercase tracking-wider">
                      +{formatCurrency(item.adjustment)} adj
                    </span>
                  </div>
                ) : (
                  <p className="text-[10px] text-muted-foreground uppercase font-bold tracking-tighter">
                    {item.type}
                  </p>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
};
