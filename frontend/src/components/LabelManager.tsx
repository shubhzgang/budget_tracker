import React, { useEffect, useState } from 'react';
import apiClient from '../api/client';
import type { Label, CreateLabelRequest } from '../types/label';

export const LabelManager: React.FC = () => {
  const [labels, setLabels] = useState<Label[]>([]);
  const [loading, setLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [name, setName] = useState('');

  const fetchLabels = async () => {
    try {
      const response = await apiClient.get('/labels');
      setLabels(response.data);
    } catch (error) {
      console.error('Failed to fetch labels', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLabels();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      await apiClient.post('/labels', { name } as CreateLabelRequest);
      setName('');
      await fetchLabels();
    } catch (error) {
      console.error('Failed to create label', error);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm('Are you sure you want to delete this label?')) return;
    try {
      await apiClient.delete(`/labels/${id}`);
      await fetchLabels();
    } catch (error) {
      console.error('Failed to delete label', error);
    }
  };

  return (
    <div className="space-y-8">
      <section className="bg-card p-6 rounded-lg border border-border shadow-sm">
        <h3 className="text-lg font-bold mb-4">Add New Label</h3>
        <form onSubmit={handleSubmit} className="flex flex-col sm:flex-row gap-4 items-end">
          <div className="flex-1 space-y-1 w-full">
            <label className="text-sm font-medium">Label Name</label>
            <input
              required
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
              placeholder="e.g. Personal, Work, Tax-Deductible"
            />
          </div>
          <button
            type="submit"
            disabled={isSubmitting}
            className="bg-primary text-primary-foreground px-6 py-2 rounded-md font-medium hover:opacity-90 transition-opacity disabled:opacity-50 h-[42px]"
          >
            {isSubmitting ? 'Adding...' : 'Add'}
          </button>
        </form>
      </section>

      <section>
        <h3 className="text-lg font-bold mb-4">Your Labels</h3>
        {loading ? (
          <div className="flex flex-wrap gap-3 animate-pulse">
            {[1, 2, 3, 4].map(i => (
              <div key={i} className="h-8 w-24 bg-secondary rounded-full"></div>
            ))}
          </div>
        ) : (
          <div className="flex flex-wrap gap-3">
            {labels.map(label => (
              <div
                key={label.id}
                className="flex items-center gap-2 px-4 py-2 bg-secondary text-secondary-foreground rounded-full border border-border group"
              >
                <span className="font-medium text-sm">{label.name}</span>
                {!label.isDefault && (
                  <button
                    onClick={() => handleDelete(label.id)}
                    className="text-muted-foreground hover:text-destructive transition-colors"
                    title="Delete Label"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" className="w-3.5 h-3.4">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                )}
                {label.isDefault && (
                  <span className="text-[8px] opacity-50 uppercase font-black">Fix</span>
                )}
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
};
