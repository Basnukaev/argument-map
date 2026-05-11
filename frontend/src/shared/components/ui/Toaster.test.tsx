import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Toaster from './Toaster';
import { useToastStore, toast } from '@/shared/stores/toastStore';

describe('Toaster', () => {
  beforeEach(() => {
    useToastStore.getState().clear();
  });

  it('пустой стор - ничего не рендерит', () => {
    render(<Toaster />);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('рендерит toasts всех типов с правильным data-testid', async () => {
    toast.error('err');
    toast.warning('warn');
    toast.info('info');
    toast.success('ok');
    render(<Toaster />);
    expect(await screen.findByTestId('toast-error')).toHaveTextContent('err');
    expect(screen.getByTestId('toast-warning')).toHaveTextContent('warn');
    expect(screen.getByTestId('toast-info')).toHaveTextContent('info');
    expect(screen.getByTestId('toast-success')).toHaveTextContent('ok');
  });

  it('крестик dismiss убирает toast', async () => {
    toast.info('тест');
    render(<Toaster />);
    await userEvent.click(await screen.findByRole('button', { name: 'Закрыть уведомление' }));
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('action-кнопка вызывает callback и закрывает', async () => {
    const onClick = vi.fn();
    toast.warning('у тебя X', { label: 'Открыть', onClick });
    render(<Toaster />);
    await userEvent.click(await screen.findByRole('button', { name: 'Открыть' }));
    expect(onClick).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
