export type AccountType = 'CREDIT_CARD' | 'CASH' | 'BANK' | 'FRIEND_LENDING';

export interface Account {
  id: string;
  name: string;
  type: AccountType;
  balance: number;
  creditLimit?: number;
  createdAt: string;
}

export interface CreateAccountRequest {
  name: string;
  type: AccountType;
  balance: number;
  creditLimit?: number;
}
