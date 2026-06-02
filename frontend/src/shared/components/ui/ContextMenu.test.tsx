import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Trash2 } from 'lucide-react';
import ContextMenu, { type ContextMenuItem } from './ContextMenu';
import { clampMenuPosition } from './contextMenuPosition';

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

describe('clampMenuPosition', () => {
  // viewport 1000x800, меню 200x150, margin 8
  const VW = 1000;
  const VH = 800;
  const MW = 200;
  const MH = 150;

  it('оставляет позицию как есть когда меню целиком влезает', () => {
    expect(clampMenuPosition(100, 100, MW, MH, VW, VH)).toEqual({ left: 100, top: 100 });
  });

  it('сдвигает влево когда меню вылезает за правый край', () => {
    // x=950 + width 200 = 1150 > 1000 → прижать к 1000-200-8=792
    const { left } = clampMenuPosition(950, 100, MW, MH, VW, VH);
    expect(left).toBe(792);
  });

  it('сдвигает вверх когда меню вылезает за нижний край', () => {
    // y=750 + height 150 = 900 > 800 → прижать к 800-150-8=642
    const { top } = clampMenuPosition(100, 750, MW, MH, VW, VH);
    expect(top).toBe(642);
  });

  it('зажимает оба края одновременно в нижне-правом углу', () => {
    expect(clampMenuPosition(990, 790, MW, MH, VW, VH)).toEqual({ left: 792, top: 642 });
  });

  it('не уходит за левый/верхний край (минимум = margin)', () => {
    expect(clampMenuPosition(-50, -50, MW, MH, VW, VH)).toEqual({ left: 8, top: 8 });
  });

  it('меню шире viewport прижимается к margin, не уходит в минус', () => {
    expect(clampMenuPosition(500, 500, 2000, 2000, VW, VH)).toEqual({ left: 8, top: 8 });
  });
});
