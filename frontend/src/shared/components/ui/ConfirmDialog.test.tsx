import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ConfirmDialog from './ConfirmDialog';
import { askConfirm, useConfirmStore } from '@/shared/stores/confirmStore';

describe('ConfirmDialog', () => {
  beforeEach(() => {
    useConfirmStore.setState({ request: null });
  });

  it('ничего не рендерит без активного запроса', () => {
    const { container } = render(<ConfirmDialog />);
    expect(container).toBeEmptyDOMElement();
  });

  it('показывает сообщение и резолвит true по кнопке подтверждения', async () => {
    render(<ConfirmDialog />);
    const promise = askConfirm({ message: 'Удалить элемент?', danger: true });
    expect(await screen.findByText('Удалить элемент?')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Подтвердить' }));
    await expect(promise).resolves.toBe(true);
  });

  it('резолвит false по кнопке отмены', async () => {
    render(<ConfirmDialog />);
    const promise = askConfirm({ message: 'X' });
    await screen.findByText('X');
    await userEvent.click(screen.getByRole('button', { name: 'Отмена' }));
    await expect(promise).resolves.toBe(false);
  });
});
