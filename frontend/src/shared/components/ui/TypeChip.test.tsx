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
    // md = h-6
    expect(screen.getByTestId('type-chip').className).toContain('h-6');
  });

  it('size=sm меняет классы высоты', () => {
    render(<TypeChip type="CLAIM" size="sm" />);
    expect(screen.getByTestId('type-chip').className).toContain('h-5');
  });

  it('uppercase + tracking - семантика акцента типа', () => {
    render(<TypeChip type="CLAIM" />);
    const chip = screen.getByTestId('type-chip');
    expect(chip.className).toContain('uppercase');
    expect(chip.className).toContain('tracking-wider');
  });
});
