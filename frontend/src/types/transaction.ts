import type { Account } from './account';
import type { Category } from './category';
import type { Label } from './label';

export type TransactionType = 'INCOME' | 'EXPENSE' | 'LEND' | 'BORROW';

export interface Transaction {
  id: string;
  amount: number;
  type: TransactionType;
  description?: string;
  transactionDate: string;
  accountId: string;
  account?: Account;
  categoryId?: string;
  category?: Category;
  labelIds?: string[];
  labels?: Label[];
  createdAt: string;
}

export interface CreateTransactionRequest {
  amount: number;
  type: TransactionType;
  description?: string;
  transactionDate: string;
  accountId: string;
  categoryId?: string;
  labelIds?: string[];
}
