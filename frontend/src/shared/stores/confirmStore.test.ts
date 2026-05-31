import { describe, it, expect, beforeEach } from 'vitest';
import { askConfirm, useConfirmStore } from './confirmStore';

describe('confirmStore', () => {
  beforeEach(() => {
    useConfirmStore.setState({ request: null });
  });

  it('askConfirm открывает запрос и резолвит true при settle(true)', async () => {
    const p = askConfirm({ message: 'Удалить?' });
    expect(useConfirmStore.getState().request?.message).toBe('Удалить?');
    useConfirmStore.getState().settle(true);
    await expect(p).resolves.toBe(true);
    expect(useConfirmStore.getState().request).toBeNull();
  });

  it('settle(false) резолвит промис как отмену', async () => {
    const p = askConfirm({ message: 'X' });
    useConfirmStore.getState().settle(false);
    await expect(p).resolves.toBe(false);
  });

  it('новый запрос отменяет предыдущий (резолвит его false)', async () => {
    const first = askConfirm({ message: 'first' });
    const second = askConfirm({ message: 'second' });
    await expect(first).resolves.toBe(false);
    expect(useConfirmStore.getState().request?.message).toBe('second');
    useConfirmStore.getState().settle(true);
    await expect(second).resolves.toBe(true);
  });
});
