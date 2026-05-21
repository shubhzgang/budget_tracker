import type { Account } from './account';
import type { Category } from './category';
import type { Label } from './label';

export interface Transfer {
  id: string;
  fromAmount: number;
  toAmount: number;
  adjustment: number;
  description?: string;
  transactionDate: string;
  fromAccountId: string;
  fromAccount?: Account;
  toAccountId: string;
  toAccount?: Account;
  categoryId?: string;
  category?: Category;
  labelId?: string;
  label?: Label;
  createdAt: string;
}

export interface CreateTransferRequest {
  fromAmount?: number;
  toAmount?: number;
  adjustment?: number;
  transactionDate: string;
  fromAccountId: string;
  toAccountId: string;
  categoryId?: string;
  labelId?: string;
  description?: string;
}
