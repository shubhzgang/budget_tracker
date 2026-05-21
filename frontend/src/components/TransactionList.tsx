import React from 'react';
import { usePreferences } from '../context/PreferenceContext';
import type { ActivityItem } from '../types/activity';

interface TransactionListProps {
  transactions: ActivityItem[];
  loading?: boolean;
}

export const TransactionList: React.FC<TransactionListProps> = ({ transactions, loading }) => {
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
            className="flex items-center justify-between p-4 bg-card rounded-lg border border-border hover:shadow-sm transition-shadow"
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
                  {item.label && (
                    <>
                      <span className="text-[10px] text-muted-foreground uppercase font-bold">•</span>
                      <span className="px-1.5 py-0.5 bg-accent text-accent-foreground text-[10px] rounded-full font-medium">
                        {item.label.name}
                      </span>
                    </>
                  )}
                </div>
              </div>
            </div>
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
        );
      })}
    </div>
  );
};
