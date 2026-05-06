import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { Analytics } from './Analytics';
import type { Transaction } from '../types/transaction';

// Mock usePreferences
vi.mock('../context/PreferenceContext', () => ({
  usePreferences: () => ({
    preferences: { currencySymbol: '₹' },
    isLoading: false,
    updatePreferences: vi.fn(),
    refreshPreferences: vi.fn(),
  }),
}));

// Mock Recharts to avoid layout issues in tests
vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: any) => <div data-testid="responsive-container">{children}</div>,
  PieChart: ({ children }: any) => <div data-testid="pie-chart">{children}</div>,
  Pie: () => <div data-testid="pie" />,
  Cell: () => <div data-testid="cell" />,
  Tooltip: () => <div data-testid="tooltip" />,
  Legend: () => <div data-testid="legend" />,
  BarChart: ({ children }: any) => <div data-testid="bar-chart">{children}</div>,
  Bar: () => <div data-testid="bar" />,
  XAxis: () => <div data-testid="x-axis" />,
  YAxis: () => <div data-testid="y-axis" />,
  CartesianGrid: () => <div data-testid="cartesian-grid" />,
}));

describe('Analytics Component', () => {
  const mockTransactions: Transaction[] = [
    {
      id: '1',
      amount: 100,
      type: 'EXPENSE',
      category: { id: 'c1', name: 'Food', icon: '🍔', isDefault: true, createdAt: '' },
      label: { id: 'l1', name: 'Needs', isDefault: true, createdAt: '' },
      transactionDate: '2026-05-01T00:00:00Z',
      accountId: 'a1',
      createdAt: ''
    }
  ];

  it('renders loading state', () => {
    render(<Analytics transactions={[]} loading={true} />);
    const loaders = document.querySelectorAll('.animate-pulse');
    expect(loaders.length).toBeGreaterThan(0);
  });

  it('renders empty state when no expense/lend transactions', () => {
    render(<Analytics transactions={[]} />);
    expect(screen.getByText(/Add some expenses to see analytics/i)).toBeInTheDocument();
  });

  it('renders charts when data is provided', () => {
    render(<Analytics transactions={mockTransactions} />);
    expect(screen.getByText(/Spending by Label/i)).toBeInTheDocument();
    expect(screen.getByText(/Top Categories/i)).toBeInTheDocument();
    expect(screen.getAllByTestId('responsive-container').length).toBeGreaterThan(0);
  });
});
