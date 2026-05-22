import { render, screen, fireEvent, act, renderHook } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { ToastProvider, useToast } from '../context/ToastContext';
import { Toaster } from './Toaster';

function TestShell({ children }: { children: React.ReactNode }) {
  return (
    <>
      {children}
      <Toaster />
    </>
  );
}

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ToastProvider><TestShell>{children}</TestShell></ToastProvider>
);

describe('Toaster', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('does not render anything when there are no toasts', () => {
    const { container } = render(<div />, { wrapper });

    expect(container.querySelector('[aria-live="polite"]')).toBeNull();
  });

  it('renders a toast when added to context', () => {
    const { result } = renderHook(() => useToast(), { wrapper });

    act(() => {
      result.current.addToast('New toast');
    });

    expect(screen.getByText('New toast')).toBeInTheDocument();
  });

  it('renders multiple toasts stacked vertically', () => {
    const { result } = renderHook(() => useToast(), { wrapper });

    act(() => {
      result.current.addToast('First toast');
      result.current.addToast('Second toast');
      result.current.addToast('Third toast');
    });

    expect(screen.getByText('First toast')).toBeInTheDocument();
    expect(screen.getByText('Second toast')).toBeInTheDocument();
    expect(screen.getByText('Third toast')).toBeInTheDocument();
  });

  it('dismisses a toast when its close button is clicked', () => {
    const { result } = renderHook(() => useToast(), { wrapper });

    act(() => {
      result.current.addToast('Dismiss me');
      result.current.addToast('Keep me');
    });

    const dismissButtons = screen.getAllByLabelText('Dismiss');
    fireEvent.click(dismissButtons[0]);

    expect(screen.queryByText('Dismiss me')).not.toBeInTheDocument();
    expect(screen.getByText('Keep me')).toBeInTheDocument();
  });

  it('renders success, error, and info toasts with theme-aware colors', () => {
    const { result } = renderHook(() => useToast(), { wrapper });

    act(() => {
      result.current.addToast('Success', 'success');
      result.current.addToast('Error', 'error');
      result.current.addToast('Info', 'info');
    });

    expect(screen.getByText('Success')).toBeInTheDocument();
    expect(screen.getByText('Error')).toBeInTheDocument();
    expect(screen.getByText('Info')).toBeInTheDocument();

    const successToast = screen.getByText('Success').closest('div');
    expect(successToast?.style.backgroundColor).toMatch(/var\(--toast-success\)/);

    const errorToast = screen.getByText('Error').closest('div');
    expect(errorToast?.style.backgroundColor).toMatch(/var\(--toast-error\)/);

    const infoToast = screen.getByText('Info').closest('div');
    expect(infoToast?.style.backgroundColor).toMatch(/var\(--toast-info\)/);
  });

  it('auto-dismisses toasts after their duration', () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useToast(), { wrapper });

    act(() => {
      result.current.addToast('Auto-gone');
    });

    expect(screen.getByText('Auto-gone')).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(3000);
    });

    expect(screen.queryByText('Auto-gone')).not.toBeInTheDocument();
  });
});
