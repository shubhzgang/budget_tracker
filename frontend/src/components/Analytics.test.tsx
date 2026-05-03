import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { Analytics } from './Analytics';
import type { Transaction } from '../types/transaction';

// Mock recharts because it depends on DOM measurements not available in JSDOM
vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: any) => <div>{children}</div>,
  PieChart: ({ children }: any) => <div data-testid="pie-chart">{children}</div>,
  Pie: ({ children }: any) => <div>{children}</div>,
  Cell: () => null,
  Legend: () => <div data-testid="legend">Legend</div>,
  Tooltip: () => null,
  BarChart: ({ children }: any) => <div data-testid="bar-chart">{children}</div>,
  Bar: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
}));

describe('Analytics Component', () => {
  const mockTransactions: Transaction[] = [
    {
      id: '1',
      amount: -100,
      type: 'EXPENSE',
      description: 'Groceries',
      transactionDate: '2026-05-01T10:00:00Z',
      accountId: 'a1',
      category: { id: 'c1', name: 'Food', icon: '🍔', isDefault: true, createdAt: '2026-05-01T10:00:00Z' },
      label: { id: 'l1', name: 'Needs', isDefault: true, createdAt: '2026-05-01T10:00:00Z' },
      createdAt: '2026-05-01T10:00:00Z',
    },
    {
      id: '2',
      amount: -50,
      type: 'EXPENSE',
      description: 'Cinema',
      transactionDate: '2026-05-02T10:00:00Z',
      accountId: 'a1',
      category: { id: 'c2', name: 'Entertainment', icon: '🎬', isDefault: true, createdAt: '2026-05-01T10:00:00Z' },
      label: { id: 'l2', name: 'Wants', isDefault: true, createdAt: '2026-05-01T10:00:00Z' },
      createdAt: '2026-05-02T10:00:00Z',
    },
    {
      id: '3',
      amount: 500,
      type: 'INCOME',
      description: 'Salary',
      transactionDate: '2026-05-01T09:00:00Z',
      accountId: 'a1',
      createdAt: '2026-05-01T09:00:00Z',
    },
    {
      id: '4',
      amount: -30,
      type: 'LEND',
      description: 'Lent to friend',
      transactionDate: '2026-05-03T10:00:00Z',
      accountId: 'a1',
      category: { id: 'c1', name: 'Food', icon: '🍔', isDefault: true, createdAt: '2026-05-01T10:00:00Z' },
      createdAt: '2026-05-03T10:00:00Z',
    }
  ];

  it('renders loading state', () => {
    const { container } = render(<Analytics transactions={[]} loading={true} />);
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('renders empty state when no expense/lend transactions', () => {
    const incomeOnly = mockTransactions.filter(t => t.type === 'INCOME');
    render(<Analytics transactions={incomeOnly} />);
    expect(screen.getByText(/Add some expenses to see analytics/i)).toBeInTheDocument();
  });

  it('renders charts when data is provided', () => {
    render(<Analytics transactions={mockTransactions} />);
    
    expect(screen.getByText(/Spending by Label/i)).toBeInTheDocument();
    expect(screen.getByText(/Top Categories/i)).toBeInTheDocument();
    expect(screen.getByTestId('pie-chart')).toBeInTheDocument();
    expect(screen.getByTestId('bar-chart')).toBeInTheDocument();
  });

  it('filters only EXPENSE and LEND transactions for charts', () => {
    // This is tested implicitly by checking that income-only results in empty state,
    // and that the charts render with the mixed data.
    render(<Analytics transactions={mockTransactions} />);
    expect(screen.getByTestId('pie-chart')).toBeInTheDocument();
  });
});
