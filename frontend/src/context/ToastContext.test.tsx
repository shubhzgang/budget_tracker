import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { ToastProvider, useToast } from './ToastContext';

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ToastProvider>{children}</ToastProvider>
);

describe('ToastContext', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('provides addToast, removeToast, and empty toasts initially', () => {
    const { result } = renderHook(() => useToast(), { wrapper });

    expect(result.current.toasts).toEqual([]);
    expect(typeof result.current.addToast).toBe('function');
    expect(typeof result.current.removeToast).toBe('function');
  });

  it('adds a success toast by default', () => {
    const { result } = renderHook(() => useToast(), { wrapper });

    act(() => {
      result.current.addToast('Hello');
    });

    expect(result.current.toasts).toHaveLength(1);
    expect(result.current.toasts[0]).toEqual(expect.objectContaining({
      type: 'success',
      message: 'Hello',
    }));
  });

  it('adds toast with specified type', () => {
    const { result } = renderHook(() => useToast(), { wrapper });

    act(() => {
      result.current.addToast('Error happened', 'error');
    });
    act(() => {
      result.current.addToast('Info message', 'info');
    });

    expect(result.current.toasts).toHaveLength(2);
    expect(result.current.toasts[0]).toEqual(expect.objectContaining({
      type: 'error',
      message: 'Error happened',
    }));
    expect(result.current.toasts[1]).toEqual(expect.objectContaining({
      type: 'info',
      message: 'Info message',
    }));
  });

  it('adds toast with custom duration', () => {
    const { result } = renderHook(() => useToast(), { wrapper });

    act(() => {
      result.current.addToast('Short-lived', 'success', 1000);
    });

    expect(result.current.toasts[0].duration).toBe(1000);
  });

  it('each toast gets a unique ID', () => {
    const { result } = renderHook(() => useToast(), { wrapper });

    act(() => {
      result.current.addToast('First');
      result.current.addToast('Second');
      result.current.addToast('Third');
    });

    const ids = result.current.toasts.map(t => t.id);
    expect(ids[0]).not.toBe(ids[1]);
    expect(ids[1]).not.toBe(ids[2]);
    expect(ids[0]).not.toBe(ids[2]);
  });

  it('removes a toast by ID', () => {
    const { result } = renderHook(() => useToast(), { wrapper });

    let removeId = '';

    act(() => {
      result.current.addToast('Keep me');
    });
    act(() => {
      result.current.addToast('Remove me');
    });

    removeId = result.current.toasts[1].id;

    act(() => {
      result.current.removeToast(removeId);
    });

    expect(result.current.toasts).toHaveLength(1);
    expect(result.current.toasts[0].message).toBe('Keep me');
  });

  it('auto-dismisses toasts after their duration', () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useToast(), { wrapper });

    act(() => {
      result.current.addToast('Auto-gone', 'success', 5000);
    });

    expect(result.current.toasts).toHaveLength(1);

    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(result.current.toasts).toHaveLength(0);
  });

  it('auto-dismisses only the expired toast when multiple are present', () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useToast(), { wrapper });

    act(() => {
      result.current.addToast('Short', 'success', 2000);
      result.current.addToast('Long', 'error', 10000);
    });

    expect(result.current.toasts).toHaveLength(2);

    act(() => {
      vi.advanceTimersByTime(2000);
    });

    expect(result.current.toasts).toHaveLength(1);
    expect(result.current.toasts[0].message).toBe('Long');

    act(() => {
      vi.advanceTimersByTime(10000);
    });

    expect(result.current.toasts).toHaveLength(0);
  });

  it('uses default duration of 3000ms', () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useToast(), { wrapper });

    act(() => {
      result.current.addToast('Default duration');
    });

    expect(result.current.toasts[0].duration).toBe(3000);

    act(() => {
      vi.advanceTimersByTime(2999);
    });

    expect(result.current.toasts).toHaveLength(1);

    act(() => {
      vi.advanceTimersByTime(1);
    });

    expect(result.current.toasts).toHaveLength(0);
  });

  it('throws when useToast is used outside provider', () => {
    expect(() => renderHook(() => useToast())).toThrow('useToast must be used within a ToastProvider');
  });
});
