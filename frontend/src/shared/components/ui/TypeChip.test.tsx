import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import TypeChip from './TypeChip';

describe('TypeChip', () => {
  it('рендерит с data-type для семантического поиска в тестах', () => {
    render(<TypeChip type="CLAIM" />);
    expect(screen.getByTestId('type-chip')).toHaveAttribute('data-type', 'CLAIM');
  });

  it('переключение type меняет data-type', () => {
    const { rerender } = render(<TypeChip type="CLAIM" />);
    expect(screen.getByTestId('type-chip')).toHaveAttribute('data-type', 'CLAIM');

    rerender(<TypeChip type="EVIDENCE" />);
    expect(screen.getByTestId('type-chip')).toHaveAttribute('data-type', 'EVIDENCE');
  });

  it('применяет классы размера md по умолчанию', () => {
    render(<TypeChip type="CLAIM" />);
    // md = px-2.5 py-1 (padding-based sizing вместо фикс. h-6 — даёт отступ
    // тексту со всех сторон, не касается границ chip)
    expect(screen.getByTestId('type-chip').className).toContain('px-2.5');
    expect(screen.getByTestId('type-chip').className).toContain('py-1');
  });

  it('size=sm меняет классы размера', () => {
    render(<TypeChip type="CLAIM" size="sm" />);
    // sm = px-2 py-1 (уже md)
    expect(screen.getByTestId('type-chip').className).toContain('px-2');
    expect(screen.getByTestId('type-chip').className).toContain('py-1');
  });

  it('uppercase + tracking - семантика акцента типа', () => {
    render(<TypeChip type="CLAIM" />);
    const chip = screen.getByTestId('type-chip');
    expect(chip.className).toContain('uppercase');
    expect(chip.className).toContain('tracking-wider');
  });
});
