import type { Account } from './account';
import type { Category } from './category';
import type { Label } from './label';

export type TransactionType = 'INCOME' | 'EXPENSE' | 'TRANSFER' | 'LEND' | 'BORROW';

export interface Transaction {
  id: string;
  amount: number;
  type: TransactionType;
  description?: string;
  transactionDate: string;
  accountId: string;
  account?: Account;
  toAccountId?: string;
  linkedAccount?: Account;
  isIncomingTransfer?: boolean;
  categoryId?: string;
  category?: Category;
  labelId?: string;
  label?: Label;
  createdAt: string;
}

export interface CreateTransactionRequest {
  amount: number;
  type: TransactionType;
  description?: string;
  transactionDate: string;
  accountId: string;
  toAccountId?: string;
  categoryId?: string;
  labelId?: string;
}
