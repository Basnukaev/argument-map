import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import Badge from './Badge';

describe('Badge', () => {
  it('отображает переданный текст', () => {
    render(<Badge>Метка</Badge>);
    expect(screen.getByText('Метка')).toBeInTheDocument();
  });

  it('применяет slate tone по умолчанию', () => {
    render(<Badge data-testid="badge">Текст</Badge>);
    // slate maps to семантический ink-100 (см. v2 token mapping)
    expect(screen.getByTestId('badge').className).toContain('bg-ink-100');
  });

  it('применяет emerald tone классы', () => {
    render(
      <Badge tone="emerald" data-testid="badge">
        Успех
      </Badge>,
    );
    expect(screen.getByTestId('badge').className).toContain('bg-ok-100');
    expect(screen.getByTestId('badge').className).toContain('text-ok-700');
  });

  it('применяет red tone для ошибок', () => {
    render(
      <Badge tone="red" data-testid="badge">
        Ошибка
      </Badge>,
    );
    expect(screen.getByTestId('badge').className).toContain('bg-err-100');
  });

  it('применяет md size по умолчанию', () => {
    render(<Badge data-testid="badge">x</Badge>);
    // md size: h-[22px]
    expect(screen.getByTestId('badge').className).toContain('h-[22px]');
  });

  it('применяет sm/lg size классы', () => {
    const { rerender } = render(
      <Badge size="sm" data-testid="badge">
        x
      </Badge>,
    );
    expect(screen.getByTestId('badge').className).toContain('h-5');

    rerender(
      <Badge size="lg" data-testid="badge">
        x
      </Badge>,
    );
    expect(screen.getByTestId('badge').className).toContain('h-7');
  });

  it('рендерит icon prop как svg слева от текста', () => {
    function FakeIcon(props: { size?: number; 'aria-hidden'?: boolean }) {
      return (
        <svg
          data-testid="badge-icon"
          width={props.size}
          aria-hidden={props['aria-hidden']}
        />
      );
    }
    render(
      <Badge icon={FakeIcon as never} data-testid="badge">
        С иконкой
      </Badge>,
    );
    expect(screen.getByTestId('badge-icon')).toBeInTheDocument();
    expect(screen.getByTestId('badge-icon')).toHaveAttribute('aria-hidden', 'true');
  });

  it('passes className на корневой span', () => {
    render(
      <Badge className="custom-class" data-testid="badge">
        x
      </Badge>,
    );
    expect(screen.getByTestId('badge').className).toContain('custom-class');
  });
});
