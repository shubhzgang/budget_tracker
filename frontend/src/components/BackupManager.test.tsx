import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BackupManager } from './BackupManager';
import { ToastProvider } from '../context/ToastContext';
import apiClient from '../api/client';

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ToastProvider>{children}</ToastProvider>
);

vi.mock('../api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  }
}));

const mockBackups = [
  {
    id: 'b1',
    filename: 'backup_2023.sql',
    format: 'SQL',
    sizeBytes: 1024,
    status: 'SUCCESS',
    createdAt: '2023-01-01T12:00:00Z'
  }
];

describe('BackupManager', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(apiClient.get).mockResolvedValue({ data: mockBackups });

    // Mock window.confirm
    window.confirm = vi.fn(() => true);

    // Mock URL.createObjectURL and other download related things
    window.URL.createObjectURL = vi.fn(() => 'mock-url');
  });

  it('renders backup history', async () => {
    render(<BackupManager />, { wrapper });

    await waitFor(() => {
      expect(screen.getByText('backup_2023.sql')).toBeInTheDocument();
      expect(screen.getByText('1 KB')).toBeInTheDocument();
    });
  });

  it('triggers SQL export on button click', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} });
    render(<BackupManager />, { wrapper });

    const exportBtn = screen.getByRole('button', { name: /Export SQL \(Full\)/i });
    fireEvent.click(exportBtn);

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith('/backups/export?format=SQL');
    });
  });

  it('handles data restoration', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} });
    render(<BackupManager />, { wrapper });

    const file = new File(['dummy content'], 'backup.sql', { type: 'text/sql' });
    const input = screen.getByLabelText(/Restore from Backup/i);

    // Simulating file upload
    fireEvent.change(input, { target: { files: [file] } });

    await waitFor(() => {
      expect(window.confirm).toHaveBeenCalled();
      expect(apiClient.post).toHaveBeenCalledWith('/backups/import', expect.any(FormData), expect.any(Object));
    });
  });

  it('handles download click', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockBackups }); // history fetch
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: new Blob(['content']) }); // download fetch

    render(<BackupManager />, { wrapper });

    await waitFor(() => {
      expect(screen.getByText('backup_2023.sql')).toBeInTheDocument();
    });

    const downloadBtn = screen.getByRole('button', { name: /Download/i });
    fireEvent.click(downloadBtn);

    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalledWith('/backups/b1/download', expect.objectContaining({ responseType: 'blob' }));
    });
  });
});
