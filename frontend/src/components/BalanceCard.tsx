import React from 'react';
import { usePreferences } from '../context/PreferenceContext';
import type { Account } from '../types/account';

interface BalanceCardProps {
  account: Account;
  onEdit?: (account: Account) => void;
}

export const BalanceCard: React.FC<BalanceCardProps> = ({ account, onEdit }) => {
  const { preferences } = usePreferences();
  const isCreditCard = account.type === 'CREDIT_CARD';
  const isLending = account.type === 'FRIEND_LENDING';

  const formatCurrency = (value: number) => {
    const symbol = preferences?.currencySymbol || '₹';
    const formatted = new Intl.NumberFormat('en-IN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(Math.abs(value));
    return `${value < 0 ? '-' : ''}${symbol}${formatted}`;
  };

  const creditUtilization = isCreditCard && account.creditLimit
    ? (Math.abs(account.balance) / account.creditLimit) * 100
    : 0;

  return (
    <div className="p-4 bg-card rounded-lg border border-border shadow-sm hover:shadow-md transition-shadow">
      <div className="flex justify-between items-start mb-2">
        <div className="flex items-center gap-2">
          <h4 className="font-semibold text-lg">{account.name}</h4>
          {onEdit && (
            <button
              onClick={() => onEdit(account)}
              className="p-1 rounded text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors"
              aria-label={`Edit ${account.name}`}
              title="Edit Account"
            >
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-3.5 h-3.5">
                <path strokeLinecap="round" strokeLinejoin="round" d="m16.862 4.487 1.687-1.688a1.875 1.875 0 1 1 2.652 2.652L6.83 21.75a4.5 4.5 0 0 1-1.897 1.13l-2.685.8.8-2.685a4.5 4.5 0 0 1 1.13-1.897L16.863 4.487Zm0 0L19.5 7.125" />
              </svg>
            </button>
          )}
        </div>
        <span className="text-xs font-medium px-2 py-1 rounded bg-secondary text-secondary-foreground">
          {account.type.replace('_', ' ')}
        </span>
      </div>

      <div className="mb-4">
        <p className="text-2xl font-bold">
          {isCreditCard ? 'Debt: ' : ''}
          {formatCurrency(isCreditCard ? Math.abs(account.balance) : account.balance)}
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
