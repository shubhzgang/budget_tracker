import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { CategoryManager } from './CategoryManager';
import { ThemeProvider } from '../context/ThemeContext';
import { ToastProvider } from '../context/ToastContext';

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider>
    <ToastProvider>{children}</ToastProvider>
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
