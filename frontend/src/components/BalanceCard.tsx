import React from 'react';
import type { Account } from '../types/account';

interface BalanceCardProps {
  account: Account;
}

export const BalanceCard: React.FC<BalanceCardProps> = ({ account }) => {
  const isCreditCard = account.type === 'CREDIT_CARD';
  const isLending = account.type === 'FRIEND_LENDING';

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(value);
  };

  const creditUtilization = isCreditCard && account.creditLimit
    ? (Math.abs(account.balance) / account.creditLimit) * 100
    : 0;

  return (
    <div className="p-4 bg-card rounded-lg border border-border shadow-sm hover:shadow-md transition-shadow">
      <div className="flex justify-between items-start mb-2">
        <h4 className="font-semibold text-lg">{account.name}</h4>
        <span className="text-xs font-medium px-2 py-1 rounded bg-secondary text-secondary-foreground">
          {account.type.replace('_', ' ')}
        </span>
      </div>

      <div className="mb-4">
        <p className="text-2xl font-bold">
          {formatCurrency(account.balance)}
        </p>
        {isLending && (
          <p className={`text-xs mt-1 ${account.balance >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {account.balance >= 0 ? 'They owe you' : 'You owe them'}
          </p>
        )}
      </div>

      {isCreditCard && account.creditLimit && (
        <div className="space-y-1">
          <div className="flex justify-between text-xs text-muted-foreground">
            <span>Utilization</span>
            <span>{creditUtilization.toFixed(1)}%</span>
          </div>
          <div className="w-full bg-secondary rounded-full h-1.5">
            <div
              className={`h-1.5 rounded-full ${creditUtilization > 80 ? 'bg-red-500' : 'bg-primary'}`}
              style={{ width: `${Math.min(creditUtilization, 100)}%` }}
            ></div>
          </div>
          <p className="text-[10px] text-muted-foreground text-right">
            Limit: {formatCurrency(account.creditLimit)}
          </p>
        </div>
      )}
    </div>
  );
};
