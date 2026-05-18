import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import FloatingActionBar from './FloatingActionBar';
import { useLocaleStore } from '@/shared/i18n/localeStore';

describe('FloatingActionBar', () => {
  beforeEach(() => {
    // фиксируем локаль ru чтобы строки в asserts были предсказуемы
    useLocaleStore.setState({ locale: 'ru' });
  });

  function defaultProps() {
    return {
      nodeCount: 0,
      edgeCount: 0,
      canWrite: true,
      onDelete: vi.fn(),
      onChangeStatus: vi.fn(),
      onClear: vi.fn(),
    };
  }

  it('не рендерится когда nodeCount=0 и edgeCount=0', () => {
    render(<FloatingActionBar {...defaultProps()} />);
    expect(screen.queryByRole('toolbar')).not.toBeInTheDocument();
  });

  it('не рендерится когда canWrite=false (даже с выделением)', () => {
    render(<FloatingActionBar {...defaultProps()} nodeCount={3} canWrite={false} />);
    expect(screen.queryByRole('toolbar')).not.toBeInTheDocument();
  });

  it('показывает счётчик "Выбрано N" когда только узлы выделены', () => {
    render(<FloatingActionBar {...defaultProps()} nodeCount={3} />);
    expect(screen.getByRole('toolbar')).toBeInTheDocument();
    // counterText содержит "Выбрано 3" - проверяем оба фрагмента
    expect(screen.getByText(/Выбрано/)).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('показывает counter_with_edges когда выделены и узлы и рёбра', () => {
    render(<FloatingActionBar {...defaultProps()} nodeCount={2} edgeCount={1} />);
    expect(screen.getByText(/связей/)).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('клик "Удалить" вызывает onDelete', async () => {
    const onDelete = vi.fn();
    render(<FloatingActionBar {...defaultProps()} nodeCount={2} onDelete={onDelete} />);
    await userEvent.click(screen.getByRole('button', { name: /Удалить/i }));
    expect(onDelete).toHaveBeenCalledOnce();
  });

  it('клик "Снять" вызывает onClear', async () => {
    const onClear = vi.fn();
    render(<FloatingActionBar {...defaultProps()} nodeCount={2} onClear={onClear} />);
    await userEvent.click(screen.getByRole('button', { name: /Снять/i }));
    expect(onClear).toHaveBeenCalledOnce();
  });

  it('клик "Изменить статус" открывает popup и выбор статуса вызывает onChangeStatus', async () => {
    const onChangeStatus = vi.fn();
    render(
      <FloatingActionBar
        {...defaultProps()}
        nodeCount={2}
        onChangeStatus={onChangeStatus}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: /Изменить статус/i }));

    // popup появился - проверяем что есть menuitem с "Устоявшийся"
    const standingItem = await screen.findByRole('menuitem', { name: /Устоявшийся/i });
    await userEvent.click(standingItem);

    expect(onChangeStatus).toHaveBeenCalledWith('STANDING');
  });

  it('busy=true блокирует Delete и Change Status (но не Clear)', () => {
    render(<FloatingActionBar {...defaultProps()} nodeCount={2} busy />);
    expect(screen.getByRole('button', { name: /Удалить/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Изменить статус/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Снять/i })).not.toBeDisabled();
  });

  it('nodeCount=0 (только edges) - Delete и Change Status disabled', () => {
    render(<FloatingActionBar {...defaultProps()} nodeCount={0} edgeCount={2} />);
    expect(screen.getByRole('toolbar')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Удалить/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Изменить статус/i })).toBeDisabled();
  });
});
