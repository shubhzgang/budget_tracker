import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { CategoryManager } from './CategoryManager';
import { ThemeProvider } from '../context/ThemeContext';
import { ToastProvider } from '../context/ToastContext';
import { Toaster } from './Toaster';

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider>
    <ToastProvider>
      {children}
      <Toaster />
    </ToastProvider>
  </ThemeProvider>
);

describe('CategoryManager', () => {
  it('renders existing categories and allows adding new one', async () => {
    render(
      <CategoryManager />,
      { wrapper }
    );

    // Should show mocked categories from handlers.ts (assuming defaults are there)
    // Wait for categories to load
    await waitFor(() => {
      expect(screen.getByText(/Your Categories/i)).toBeInTheDocument();
    });

    // Add new category
    const nameInput = screen.getByPlaceholderText(/e.g. Groceries/i);
    const addButton = screen.getByRole('button', { name: /Add/i });

    fireEvent.change(nameInput, { target: { value: 'New Test Category' } });
    fireEvent.click(addButton);

    await waitFor(() => {
      expect(nameInput).toHaveValue('');
    });
  });

  it('shows the backend error message when the category name is a duplicate', async () => {
    server.use(
      http.post('/api/v1/categories', () =>
        HttpResponse.json({ message: 'A category named "Food" already exists' }, { status: 400 })
      )
    );

    render(<CategoryManager />, { wrapper });

    await waitFor(() => {
      expect(screen.getByText(/Your Categories/i)).toBeInTheDocument();
    });

    fireEvent.change(screen.getByPlaceholderText(/e.g. Groceries/i), { target: { value: 'Food' } });
    fireEvent.click(screen.getByRole('button', { name: /Add/i }));

    await waitFor(() => {
      expect(screen.getByText(/A category named "Food" already exists/i)).toBeInTheDocument();
    });
  });

  it('allows deleting a custom category', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockImplementation(() => true);

    render(
      <CategoryManager />,
      { wrapper }
    );

    // Wait for categories to load
    await waitFor(() => {
      expect(screen.getByText('Rent')).toBeInTheDocument();
    });

    // Custom categories should have a delete button (mock 'Rent' is isDefault: false)
    const deleteButton = screen.getByTitle(/Delete Category/i);
    fireEvent.click(deleteButton);

    expect(confirmSpy).toHaveBeenCalled();

    confirmSpy.mockRestore();
  });
});
