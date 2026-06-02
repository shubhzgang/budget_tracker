import type { Account } from './account';
import type { Category } from './category';
import type { Label } from './label';

export type ActivityKind = 'TRANSACTION' | 'TRANSFER';
export type ActivityType = 'INCOME' | 'EXPENSE' | 'TRANSFER' | 'LEND' | 'BORROW';

export interface ActivityItem {
  id: string;
  kind: ActivityKind;
  type: ActivityType;
  amount?: number;        // present for transactions
  fromAmount?: number;    // present for transfers
  toAmount?: number;      // present for transfers
  adjustment?: number;    // present for transfers
  description?: string;
  transactionDate: string;
  account?: Account;
  toAccount?: Account;
  category?: Category;
  labels?: Label[];
  createdAt: string;
}
