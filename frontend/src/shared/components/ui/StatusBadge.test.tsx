import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import StatusBadge from './StatusBadge';

describe('StatusBadge', () => {
  it('STANDING - имеет data-status и переведённый label', () => {
    render(<StatusBadge status="STANDING" />);
    const badge = screen.getByTestId('status-badge');
    expect(badge).toHaveAttribute('data-status', 'STANDING');
    // ru-локаль по умолчанию из dictionary
    expect(badge.textContent).toMatch(/Устоявшийся|standing|مستقر/i);
  });

  it('DISPUTED - data-status переключается', () => {
    render(<StatusBadge status="DISPUTED" />);
    expect(screen.getByTestId('status-badge')).toHaveAttribute('data-status', 'DISPUTED');
  });

  it('REFUTED - data-status переключается', () => {
    render(<StatusBadge status="REFUTED" />);
    expect(screen.getByTestId('status-badge')).toHaveAttribute('data-status', 'REFUTED');
  });

  it('showIcon=false скрывает иконку - в DOM только текстовый label', () => {
    const { container } = render(<StatusBadge status="STANDING" showIcon={false} />);
    expect(container.querySelector('svg')).toBeNull();
  });

  it('showIcon=true (default) - svg иконка присутствует', () => {
    const { container } = render(<StatusBadge status="STANDING" />);
    expect(container.querySelector('svg')).not.toBeNull();
  });

  it('размер sm применяет h-5 класс', () => {
    render(<StatusBadge status="STANDING" size="sm" />);
    expect(screen.getByTestId('status-badge')).toHaveClass('h-5');
  });

  it('передаётся className через prop', () => {
    render(<StatusBadge status="STANDING" className="custom-class" />);
    expect(screen.getByTestId('status-badge')).toHaveClass('custom-class');
  });
});
