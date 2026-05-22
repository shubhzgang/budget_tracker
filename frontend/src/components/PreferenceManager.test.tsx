import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { PreferenceManager } from './PreferenceManager';
import { ToastProvider } from '../context/ToastContext';
import apiClient from '../api/client';

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ToastProvider>{children}</ToastProvider>
);

vi.mock('../api/client', () => ({
  default: {
    get: vi.fn(),
  }
}));

const mockUpdatePreferences = vi.fn();
const mockPreferences = {
  defaultAccountId: 'a1',
  defaultTransactionType: 'EXPENSE',
  defaultCategoryId: 'c1',
  defaultLabelId: 'l1',
  currencySymbol: '₹',
  autoBackupEnabled: true,
  autoBackupFrequency: 'WEEKLY',
  autoBackupFormat: 'CSV'
};

vi.mock('../context/PreferenceContext', () => ({
  usePreferences: () => ({
    preferences: mockPreferences,
    updatePreferences: mockUpdatePreferences,
    isLoading: false
  })
}));

describe('PreferenceManager', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(apiClient.get).mockImplementation((url: string) => {
      if (url === '/accounts') return Promise.resolve({ data: [{ id: 'a1', name: 'Acc 1' }] });
      if (url === '/categories') return Promise.resolve({ data: [{ id: 'c1', name: 'Cat 1', icon: '💰' }] });
      if (url === '/labels') return Promise.resolve({ data: [{ id: 'l1', name: 'Lab 1' }] });
      return Promise.resolve({ data: [] });
    });
  });

  it('renders with existing preferences', async () => {
    render(<PreferenceManager />, { wrapper });

    await waitFor(() => {
      expect(screen.getByLabelText(/Default Account/i)).toHaveValue('a1');
      expect(screen.getByLabelText(/Default Type/i)).toHaveValue('EXPENSE');
      expect(screen.getByLabelText(/Currency Symbol/i)).toHaveValue('₹');
      expect(screen.getByLabelText(/Enable Automatic Backups/i)).toBeChecked();
      expect(screen.getByLabelText(/Frequency/i)).toHaveValue('WEEKLY');
      expect(screen.getByLabelText(/Format/i)).toHaveValue('CSV');
    });
  });

  it('calls updatePreferences on submit', async () => {
    render(<PreferenceManager />, { wrapper });

    await waitFor(() => {
      expect(screen.getByLabelText(/Default Type/i)).toHaveValue('EXPENSE');
    });

    fireEvent.change(screen.getByLabelText(/Default Type/i), { target: { value: 'INCOME' } });
    fireEvent.change(screen.getByLabelText(/Currency Symbol/i), { target: { value: '$' } });
    fireEvent.click(screen.getByLabelText(/Enable Automatic Backups/i)); // Toggle to false

    fireEvent.click(screen.getByRole('button', { name: /Save Preferences/i }));

    expect(mockUpdatePreferences).toHaveBeenCalledWith(expect.objectContaining({
      defaultTransactionType: 'INCOME',
      currencySymbol: '$',
      autoBackupEnabled: false
    }));
  });
});
