import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { useToastStore, toast } from './toastStore';

describe('toastStore', () => {
  beforeEach(() => {
    useToastStore.getState().clear();
  });

  it('show добавляет toast с уникальным id', () => {
    const id1 = toast.info('первый');
    const id2 = toast.warning('второй');
    expect(id1).not.toBe(id2);
    const toasts = useToastStore.getState().toasts;
    expect(toasts).toHaveLength(2);
    expect(toasts[0]?.message).toBe('первый');
    expect(toasts[1]?.kind).toBe('warning');
  });

  it('dismiss убирает по id', () => {
    const id = toast.error('err');
    useToastStore.getState().dismiss(id);
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it('clear убирает все', () => {
    toast.info('a');
    toast.info('b');
    toast.info('c');
    useToastStore.getState().clear();
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  describe('auto-dismiss по ttl', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('warning исчезает через 6 секунд по дефолту', () => {
      toast.warning('исчезни');
      expect(useToastStore.getState().toasts).toHaveLength(1);
      vi.advanceTimersByTime(5999);
      expect(useToastStore.getState().toasts).toHaveLength(1);
      vi.advanceTimersByTime(2);
      expect(useToastStore.getState().toasts).toHaveLength(0);
    });

    it('кастомный ttl=0 не auto-dismiss', () => {
      useToastStore.getState().show({ kind: 'info', message: 'sticky', ttl: 0 });
      vi.advanceTimersByTime(60_000);
      expect(useToastStore.getState().toasts).toHaveLength(1);
    });
  });
});
