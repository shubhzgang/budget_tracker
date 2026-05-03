import React from 'react';
import type { Transaction } from '../types/transaction';

interface TransactionListProps {
  transactions: Transaction[];
  loading?: boolean;
}

export const TransactionList: React.FC<TransactionListProps> = ({ transactions, loading }) => {
  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(value);
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
      {transactions.map((transaction) => (
        <div
          key={transaction.id}
          className="flex items-center justify-between p-4 bg-card rounded-lg border border-border hover:shadow-sm transition-shadow"
        >
          <div className="flex items-center gap-4">
            <div className="w-10 h-10 rounded-full bg-secondary flex items-center justify-center text-xl">
              {transaction.category?.icon || '💸'}
            </div>
            <div>
              <p className="font-semibold text-sm">
                {transaction.type === 'TRANSFER'
                  ? (transaction.isIncomingTransfer 
                      ? `Transfer from ${transaction.linkedAccount?.name}` 
                      : `Transfer to ${transaction.linkedAccount?.name}`)
                  : transaction.description || transaction.category?.name || 'No description'}
              </p>
              <div className="flex items-center gap-2 mt-0.5">
                <span className="text-xs text-muted-foreground">{formatDate(transaction.transactionDate)}</span>
                <span className="text-[10px] text-muted-foreground uppercase font-bold">•</span>
                <span className="text-xs text-muted-foreground">{transaction.account?.name}</span>
                {transaction.label && (
                  <>
                    <span className="text-[10px] text-muted-foreground uppercase font-bold">•</span>
                    <span className="px-1.5 py-0.5 bg-accent text-accent-foreground text-[10px] rounded-full font-medium">
                      {transaction.label.name}
                    </span>
                  </>
                )}
              </div>
            </div>
          </div>
          <div className="text-right">
            <p
              className={`font-bold ${
                transaction.type === 'INCOME' || transaction.type === 'BORROW' || transaction.isIncomingTransfer
                  ? 'text-green-500'
                  : transaction.type === 'TRANSFER' && !transaction.isIncomingTransfer
                  ? 'text-foreground'
                  : 'text-red-500'
              }`}
            >
              {transaction.type === 'INCOME' || transaction.type === 'BORROW' || transaction.isIncomingTransfer ? '+' : '-'}
              {formatCurrency(Math.abs(transaction.amount))}
            </p>
            <p className="text-[10px] text-muted-foreground uppercase font-bold tracking-tighter">
              {transaction.type}
            </p>
          </div>
        </div>
      ))}
    </div>
  );
};
