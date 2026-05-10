import React, { useState, useEffect } from 'react';
import apiClient from '../api/client';

interface BackupRecord {
  id: string;
  filename: string;
  format: string;
  sizeBytes: number;
  status: string;
  createdAt: string;
}

export const BackupManager: React.FC = () => {
  const [history, setHistory] = useState<BackupRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isExporting, setIsExporting] = useState(false);
  const [isImporting, setIsImporting] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error', text: string } | null>(null);

  const fetchHistory = async () => {
    try {
      const response = await apiClient.get<BackupRecord[]>('/backups');
      setHistory(response.data);
    } catch (error) {
      console.error('Failed to fetch backup history', error);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchHistory();
  }, []);

  const handleExport = async (format: 'SQL' | 'CSV') => {
    setIsExporting(true);
    setMessage(null);
    try {
      await apiClient.post(`/backups/export?format=${format}`);
      setMessage({ type: 'success', text: `Manual ${format} export triggered successfully.` });
      fetchHistory();
    } catch (error) {
      setMessage({ type: 'error', text: 'Failed to trigger export.' });
    } finally {
      setIsExporting(false);
    }
  };

  const handleImport = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const confirmRestore = window.confirm("Restoring from backup may overwrite current data. Are you sure you want to proceed?");
    if (!confirmRestore) return;

    setIsImporting(true);
    setMessage(null);
    const formData = new FormData();
    formData.append('file', file);

    try {
      await apiClient.post('/backups/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      setMessage({ type: 'success', text: 'Data restored successfully. Please refresh the page.' });
      // Reset the file input
      e.target.value = '';
    } catch (error) {
      setMessage({ type: 'error', text: 'Failed to restore data.' });
    } finally {
      setIsImporting(false);
    }
  };

  const handleDownload = async (record: BackupRecord) => {
    try {
      const response = await apiClient.get(`/backups/${record.id}/download`, {
        responseType: 'blob'
      });
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', record.filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (error) {
      console.error('Download failed', error);
    }
  };

  const handleDeleteAllData = async () => {
    const confirmDelete = window.confirm("Are you sure you want to delete ALL your data? This cannot be undone.");
    if (!confirmDelete) return;

    setIsLoading(true);
    setMessage(null);
    try {
      await apiClient.delete('/backups/clear');
      setMessage({ type: 'success', text: 'All data has been deleted.' });
      fetchHistory();
    } catch (error) {
      setMessage({ type: 'error', text: 'Failed to delete data.' });
    } finally {
      setIsLoading(false);
    }
  };

  const formatSize = (bytes: number) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  return (
    <div className="bg-card border border-border rounded-xl p-6 space-y-8">
      <div>
        <h3 className="text-lg font-bold mb-1">Data Management</h3>
        <p className="text-sm text-muted-foreground mb-6">
          Export your data for backup or restore it from a previous file.
        </p>

        {message && (
          <div className={`p-4 rounded-md mb-6 ${message.type === 'success' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
            {message.text}
          </div>
        )}

        <div className="flex flex-wrap gap-4 mb-8">
          <button
            onClick={() => handleExport('SQL')}
            disabled={isExporting}
            className="bg-primary text-primary-foreground px-4 py-2 rounded-md font-semibold hover:opacity-90 disabled:opacity-50"
          >
            {isExporting ? 'Exporting SQL...' : 'Export SQL (Full)'}
          </button>
          <button
            onClick={() => handleExport('CSV')}
            disabled={isExporting}
            className="bg-secondary text-secondary-foreground px-4 py-2 rounded-md font-semibold hover:opacity-90 disabled:opacity-50"
          >
            {isExporting ? 'Exporting CSV...' : 'Export CSV (Transactions)'}
          </button>
          
          <div className="relative">
            <input
              type="file"
              id="restore-upload"
              className="hidden"
              accept=".sql,.csv"
              onChange={handleImport}
              disabled={isImporting}
            />
            <label
              htmlFor="restore-upload"
              className={`inline-block border border-input px-4 py-2 rounded-md font-semibold cursor-pointer hover:bg-muted transition-colors ${isImporting ? 'opacity-50 cursor-not-allowed' : ''}`}
            >
              {isImporting ? 'Restoring...' : 'Restore from Backup'}
            </label>
          </div>

          <button
            onClick={handleDeleteAllData}
            disabled={isLoading}
            className="bg-red-500 text-white px-4 py-2 rounded-md font-semibold hover:bg-red-600 disabled:opacity-50"
          >
            Delete All Data
          </button>
        </div>

        <h4 className="text-md font-bold mb-4">Backup History (on server)</h4>
        {isLoading ? (
          <div className="animate-pulse space-y-2">
            {[1, 2, 3].map(i => <div key={i} className="h-10 bg-muted rounded"></div>)}
          </div>
        ) : history.length === 0 ? (
          <p className="text-sm text-muted-foreground italic">No backups found.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="bg-muted text-muted-foreground uppercase text-xs">
                <tr>
                  <th className="px-4 py-2">Date</th>
                  <th className="px-4 py-2">Filename</th>
                  <th className="px-4 py-2">Format</th>
                  <th className="px-4 py-2">Size</th>
                  <th className="px-4 py-2 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {history.map(record => (
                  <tr key={record.id} className="hover:bg-muted/50">
                    <td className="px-4 py-3">{new Date(record.createdAt).toLocaleString()}</td>
                    <td className="px-4 py-3 font-mono text-xs">{record.filename}</td>
                    <td className="px-4 py-3"><span className="px-2 py-1 bg-secondary rounded text-[10px] font-bold">{record.format}</span></td>
                    <td className="px-4 py-3 text-muted-foreground">{formatSize(record.sizeBytes)}</td>
                    <td className="px-4 py-3 text-right">
                      <button
                        onClick={() => handleDownload(record)}
                        className="text-primary hover:underline font-semibold"
                      >
                        Download
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
