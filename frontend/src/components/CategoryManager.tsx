import React, { useEffect, useState } from 'react';
import apiClient from '../api/client';
import type { Category, CreateCategoryRequest } from '../types/category';

const AVAILABLE_ICONS = [
  '🍔', '🛒', '🚗', '🏠', '💡', '🏥', '🎓', '🎬', '✈️', '🎁', '💰', '📉', '🛠️', '📱', '👕'
];

export const CategoryManager: React.FC = () => {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formData, setFormData] = useState<CreateCategoryRequest>({
    name: '',
    icon: AVAILABLE_ICONS[0]
  });

  const fetchCategories = async () => {
    try {
      const response = await apiClient.get('/categories');
      setCategories(response.data);
    } catch (error) {
      console.error('Failed to fetch categories', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCategories();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      await apiClient.post('/categories', formData);
      setFormData({ name: '', icon: AVAILABLE_ICONS[0] });
      await fetchCategories();
    } catch (error) {
      console.error('Failed to create category', error);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm('Are you sure you want to delete this category?')) return;
    try {
      await apiClient.delete(`/categories/${id}`);
      await fetchCategories();
    } catch (error) {
      console.error('Failed to delete category', error);
    }
  };

  return (
    <div className="space-y-8">
      <section className="bg-card p-6 rounded-lg border border-border shadow-sm">
        <h3 className="text-lg font-bold mb-4">Add New Category</h3>
        <form onSubmit={handleSubmit} className="flex flex-col sm:flex-row gap-4 items-end">
          <div className="flex-1 space-y-1 w-full">
            <label className="text-sm font-medium">Category Name</label>
            <input
              required
              type="text"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
              placeholder="e.g. Groceries"
            />
          </div>
          <div className="space-y-1 w-full sm:w-32">
            <label className="text-sm font-medium">Icon</label>
            <select
              value={formData.icon}
              onChange={(e) => setFormData({ ...formData, icon: e.target.value })}
              className="w-full border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
            >
              {AVAILABLE_ICONS.map(icon => (
                <option key={icon} value={icon}>{icon}</option>
              ))}
            </select>
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
        <h3 className="text-lg font-bold mb-4">Your Categories</h3>
        {loading ? (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 animate-pulse">
            {[1, 2, 3, 4].map(i => (
              <div key={i} className="h-16 bg-secondary rounded-lg"></div>
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
            {categories.map(category => (
              <div
                key={category.id}
                className="flex items-center justify-between p-4 bg-card rounded-lg border border-border group"
              >
                <div className="flex items-center gap-3">
                  <span className="text-2xl">{category.icon}</span>
                  <span className="font-medium">{category.name}</span>
                </div>
                {!category.isDefault && (
                  <button
                    onClick={() => handleDelete(category.id)}
                    className="text-destructive opacity-0 group-hover:opacity-100 transition-opacity p-1 hover:bg-destructive/10 rounded"
                    title="Delete Category"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-4 h-4">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
                    </svg>
                  </button>
                )}
                {category.isDefault && (
                  <span className="text-[10px] text-muted-foreground uppercase font-bold tracking-tighter">Default</span>
                )}
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
};
