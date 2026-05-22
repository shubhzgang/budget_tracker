import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { Toast } from './Toast';
import type { Toast as ToastType } from '../types/toast';

const makeToast = (overrides: Partial<ToastType> = {}): ToastType => ({
  id: 'toast-1',
  type: 'success',
  message: 'Test message',
  ...overrides,
});

describe('Toast', () => {
  it('renders success toast message', () => {
    render(<Toast toast={makeToast({ type: 'success' })} onClose={() => {}} />);

    expect(screen.getByText('Test message')).toBeInTheDocument();
  });

  it('renders error toast message', () => {
    render(<Toast toast={makeToast({ type: 'error' })} onClose={() => {}} />);

    expect(screen.getByText('Test message')).toBeInTheDocument();
  });

  it('renders info toast message', () => {
    render(<Toast toast={makeToast({ type: 'info' })} onClose={() => {}} />);

    expect(screen.getByText('Test message')).toBeInTheDocument();
  });

  it('has dismiss button for all types', () => {
    render(<Toast toast={makeToast()} onClose={() => {}} />);

    expect(screen.getByLabelText('Dismiss')).toBeInTheDocument();
  });

  it('calls onClose when dismiss button is clicked', () => {
    const onClose = vi.fn();
    render(<Toast toast={makeToast()} onClose={onClose} />);

    fireEvent.click(screen.getByLabelText('Dismiss'));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('applies success background via CSS variable', () => {
    const { container } = render(<Toast toast={makeToast({ type: 'success' })} onClose={() => {}} />);

    expect(container.firstChild).toHaveAttribute('style');
    expect((container.firstChild as HTMLElement)?.style.backgroundColor).toMatch(/var\(--toast-success\)/);
  });

  it('applies error background via CSS variable', () => {
    const { container } = render(<Toast toast={makeToast({ type: 'error' })} onClose={() => {}} />);

    expect((container.firstChild as HTMLElement)?.style.backgroundColor).toMatch(/var\(--toast-error\)/);
  });

  it('applies info background via CSS variable', () => {
    const { container } = render(<Toast toast={makeToast({ type: 'info' })} onClose={() => {}} />);

    expect((container.firstChild as HTMLElement)?.style.backgroundColor).toMatch(/var\(--toast-info\)/);
  });
});
