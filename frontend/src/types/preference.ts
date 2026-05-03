import type { TransactionType } from './transaction';

export interface UserPreference {
  userId: string;
  defaultAccountId: string | null;
  defaultTransactionType: TransactionType | null;
  defaultCategoryId: string | null;
  defaultLabelId: string | null;
}

export interface UserPreferenceRequest {
  defaultAccountId: string | null;
  defaultTransactionType: TransactionType | null;
  defaultCategoryId: string | null;
  defaultLabelId: string | null;
}
