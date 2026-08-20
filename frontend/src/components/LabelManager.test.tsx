import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { LabelManager } from './LabelManager';
import { ThemeProvider } from '../context/ThemeContext';
import { ToastProvider } from '../context/ToastContext';
import { UIProvider } from '../context/UIContext';

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider>
    <ToastProvider>
      <UIProvider>{children}</UIProvider>
    </ToastProvider>
  </ThemeProvider>
);

describe('LabelManager', () => {
  it('renders existing labels and allows adding new one', async () => {
    render(
      <LabelManager />,
      { wrapper }
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
      <LabelManager />,
      { wrapper }
    );

    await waitFor(() => {
      expect(screen.getByText('Work')).toBeInTheDocument();
    });

    const deleteButton = screen.getByTitle(/Delete Label/i);
    fireEvent.click(deleteButton);

    expect(confirmSpy).toHaveBeenCalled();

    confirmSpy.mockRestore();
  });

  it('marks the default label with a DEFAULT badge instead of a delete button', async () => {
    render(
      <LabelManager />,
      { wrapper }
    );

    await waitFor(() => {
      expect(screen.getByText('Personal')).toBeInTheDocument();
    });

    expect(screen.getByText('Default')).toBeInTheDocument();
    expect(screen.queryByText('Fix')).not.toBeInTheDocument();
  });

  it('does not show a delete button for the default label', async () => {
    render(
      <LabelManager />,
      { wrapper }
    );

    await waitFor(() => {
      expect(screen.getByText('Personal')).toBeInTheDocument();
    });

    const labelPill = screen.getByText('Personal').closest('div');
    expect(labelPill).not.toBeNull();
    expect(labelPill!.querySelector('[title="Delete Label"]')).toBeNull();
  });
});
