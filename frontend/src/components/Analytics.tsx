import React, { useMemo } from 'react';
import {
  PieChart,
  Pie,
  Cell,
  ResponsiveContainer,
  Legend,
  Tooltip,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid
} from 'recharts';
import type { Transaction } from '../types/transaction';

interface AnalyticsProps {
  transactions: Transaction[];
  loading?: boolean;
}

const COLORS = ['#8884d8', '#82ca9d', '#ffc658', '#ff8042', '#0088fe', '#00C49F', '#FFBB28', '#FF8042'];

export const Analytics: React.FC<AnalyticsProps> = ({ transactions, loading }) => {
  const expenseTransactions = useMemo(() => 
    transactions.filter(t => t.type === 'EXPENSE' || t.type === 'LEND'),
    [transactions]
  );

  const labelData = useMemo(() => {
    const data: Record<string, number> = {};
    expenseTransactions.forEach(t => {
      const labelName = t.label?.name || 'Unlabeled';
      data[labelName] = (data[labelName] || 0) + Math.abs(t.amount);
    });
    return Object.entries(data)
      .map(([name, value]) => ({ name, value }))
      .sort((a, b) => b.value - a.value);
  }, [expenseTransactions]);

  const categoryData = useMemo(() => {
    const data: Record<string, number> = {};
    expenseTransactions.forEach(t => {
      const catName = t.category?.name || 'Uncategorized';
      data[catName] = (data[catName] || 0) + Math.abs(t.amount);
    });
    return Object.entries(data)
      .map(([name, value]) => ({ name, value }))
      .sort((a, b) => b.value - a.value)
      .slice(0, 8); // Top 8 categories
  }, [expenseTransactions]);

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 0
    }).format(value);
  };

  if (loading) {
    return (
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 animate-pulse">
        <div className="h-80 bg-secondary rounded-xl"></div>
        <div className="h-80 bg-secondary rounded-xl"></div>
      </div>
    );
  }

  if (expenseTransactions.length === 0) {
    return (
      <div className="text-center py-20 bg-card rounded-xl border border-dashed border-border">
        <p className="text-muted-foreground">Add some expenses to see analytics.</p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
      {/* Spending by Label */}
      <div className="bg-card p-6 rounded-xl border border-border shadow-sm">
        <h3 className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-6 flex items-center gap-2">
          Spending by Label
          <span className="h-[1px] bg-border flex-grow"></span>
        </h3>
        <div className="h-64 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={labelData}
                cx="50%"
                cy="50%"
                innerRadius={60}
                outerRadius={80}
                paddingAngle={5}
                dataKey="value"
              >
                {labelData.map((_entry, index) => (
                  <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip 
                formatter={(value: any) => formatCurrency(Number(value))}
                contentStyle={{ 
                  backgroundColor: 'var(--card)', 
                  borderColor: 'var(--border)', 
                  borderRadius: '8px',
                  fontSize: '12px',
                  color: 'var(--foreground)'
                }}
              />
              <Legend verticalAlign="bottom" height={36} iconType="circle" />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Spending by Category */}
      <div className="bg-card p-6 rounded-xl border border-border shadow-sm">
        <h3 className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-6 flex items-center gap-2">
          Top Categories
          <span className="h-[1px] bg-border flex-grow"></span>
        </h3>
        <div className="h-64 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={categoryData} layout="vertical" margin={{ left: 0, right: 30 }}>
              <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="var(--border)" />
              <XAxis type="number" hide />
              <YAxis 
                dataKey="name" 
                type="category" 
                width={100} 
                tick={{ fontSize: 10, fill: 'var(--muted-foreground)', fontWeight: 600 }}
                axisLine={false}
                tickLine={false}
              />
              <Tooltip 
                cursor={{ fill: 'var(--secondary)', opacity: 0.4 }}
                formatter={(value: any) => formatCurrency(Number(value))}
                contentStyle={{ 
                  backgroundColor: 'var(--card)', 
                  borderColor: 'var(--border)', 
                  borderRadius: '8px',
                  fontSize: '12px',
                  color: 'var(--foreground)'
                }}
              />
              <Bar 
                dataKey="value" 
                fill="var(--primary)" 
                radius={[0, 4, 4, 0]} 
                barSize={20}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
};
