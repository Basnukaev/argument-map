import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import Chip from './Chip';

describe('Chip', () => {
  it('отображает переданный текст', () => {
    render(<Chip>метка</Chip>);
    expect(screen.getByText('метка')).toBeInTheDocument();
  });

  it('по умолчанию nейтральный bg-ink-100', () => {
    render(<Chip data-testid="chip">x</Chip>);
    expect(screen.getByTestId('chip').className).toContain('bg-ink-100');
  });

  it('accent=true → accent palette', () => {
    render(
      <Chip accent data-testid="chip">
        x
      </Chip>,
    );
    expect(screen.getByTestId('chip').className).toContain('bg-accent-100');
    expect(screen.getByTestId('chip').className).toContain('text-accent-700');
  });

  it('ok=true → success palette', () => {
    render(
      <Chip ok data-testid="chip">
        x
      </Chip>,
    );
    expect(screen.getByTestId('chip').className).toContain('bg-ok-100');
  });

  it('icon рендерится как ReactNode перед текстом', () => {
    render(
      <Chip icon={<span data-testid="chip-icon">★</span>} data-testid="chip">
        текст
      </Chip>,
    );
    expect(screen.getByTestId('chip-icon')).toBeInTheDocument();
  });

  it('custom className пробрасывается на span', () => {
    render(
      <Chip className="my-extra" data-testid="chip">
        x
      </Chip>,
    );
    expect(screen.getByTestId('chip').className).toContain('my-extra');
  });
});
