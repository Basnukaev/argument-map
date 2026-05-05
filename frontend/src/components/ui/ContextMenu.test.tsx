import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Trash2 } from 'lucide-react';
import ContextMenu, { type ContextMenuItem } from './ContextMenu';

const items: ContextMenuItem[] = [
  { id: '1', label: 'Редактировать', onClick: vi.fn() },
  { id: '2', label: 'Удалить', icon: Trash2, danger: true, onClick: vi.fn() },
];

describe('ContextMenu', () => {
  it('рендерит все пункты как menuitem', () => {
    render(<ContextMenu x={0} y={0} items={items} onClose={vi.fn()} />);
    expect(screen.getByRole('menuitem', { name: /Редактировать/ })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /Удалить/ })).toBeInTheDocument();
  });

  it('header рендерится над пунктами если задан', () => {
    render(<ContextMenu x={0} y={0} items={items} onClose={vi.fn()} header="Узел" />);
    expect(screen.getByText('Узел')).toBeInTheDocument();
  });

  it('клик по пункту вызывает onClick + onClose', async () => {
    const onClick = vi.fn();
    const onClose = vi.fn();
    render(
      <ContextMenu
        x={0}
        y={0}
        items={[{ id: 'a', label: 'Действие', onClick }]}
        onClose={onClose}
      />,
    );
    await userEvent.click(screen.getByRole('menuitem', { name: 'Действие' }));
    expect(onClick).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('Escape закрывает меню', async () => {
    const onClose = vi.fn();
    render(<ContextMenu x={0} y={0} items={items} onClose={onClose} />);
    await userEvent.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });

  it('клик вне меню закрывает', async () => {
    const onClose = vi.fn();
    render(
      <div>
        <button type="button">снаружи</button>
        <ContextMenu x={0} y={0} items={items} onClose={onClose} />
      </div>,
    );
    await userEvent.pointer({ keys: '[MouseLeft>]', target: screen.getByText('снаружи') });
    expect(onClose).toHaveBeenCalled();
  });

  it('disabled пункт не вызывает onClick', async () => {
    const onClick = vi.fn();
    render(
      <ContextMenu
        x={0}
        y={0}
        items={[{ id: 'a', label: 'Off', disabled: true, onClick }]}
        onClose={vi.fn()}
      />,
    );
    await userEvent.click(screen.getByRole('menuitem', { name: 'Off' }));
    expect(onClick).not.toHaveBeenCalled();
  });
});
