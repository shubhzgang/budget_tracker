import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { LabelManager } from './LabelManager';
import { ThemeProvider } from '../context/ThemeContext';

describe('LabelManager', () => {
  it('renders existing labels and allows adding new one', async () => {
    render(
      <ThemeProvider>
        <LabelManager />
      </ThemeProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/Your Labels/i)).toBeInTheDocument();
    });

    const nameInput = screen.getByPlaceholderText(/e.g. Personal, Work/i);
    const addButton = screen.getByRole('button', { name: /Add/i });

    fireEvent.change(nameInput, { target: { value: 'New Test Label' } });
    fireEvent.click(addButton);

    await waitFor(() => {
      expect(nameInput).toHaveValue('');
    });
  });

  it('allows deleting a custom label', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockImplementation(() => true);
    
    render(
      <ThemeProvider>
        <LabelManager />
      </ThemeProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Work')).toBeInTheDocument();
    });

    const deleteButton = screen.getByTitle(/Delete Label/i);
    fireEvent.click(deleteButton);

    expect(confirmSpy).toHaveBeenCalled();
    
    confirmSpy.mockRestore();
  });
});
