import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Button from './Button';

describe('Button', () => {
  it('отображает переданный текст', () => {
    render(<Button>Создать тему</Button>);
    expect(screen.getByRole('button', { name: 'Создать тему' })).toBeInTheDocument();
  });

  it('вызывает onClick при клике', async () => {
    const handleClick = vi.fn();
    const user = userEvent.setup();
    render(<Button onClick={handleClick}>Жми</Button>);

    await user.click(screen.getByRole('button', { name: 'Жми' }));

    expect(handleClick).toHaveBeenCalledOnce();
  });

  it('применяет классы варианта secondary', () => {
    render(<Button variant="secondary">Отмена</Button>);
    expect(screen.getByRole('button')).toHaveClass('bg-white');
  });

  it('primary - default variant', () => {
    render(<Button>Создать</Button>);
    // T-05: семантический check через data-variant вместо хрупкого
    // toHaveClass('bg-indigo-600') (палитра может меняться)
    expect(screen.getByRole('button')).toHaveAttribute('data-variant', 'primary');
  });

  it('рендерит icon prop как иконку слева', () => {
    function FakeIcon(props: { size?: number; 'aria-hidden'?: boolean }) {
      return <svg data-testid="fake-icon" width={props.size} aria-hidden={props['aria-hidden']} />;
    }
    render(<Button icon={FakeIcon as never}>Создать</Button>);
    expect(screen.getByTestId('fake-icon')).toBeInTheDocument();
  });

  it('disabled блокирует клик', async () => {
    const handleClick = vi.fn();
    const user = userEvent.setup();
    render(
      <Button onClick={handleClick} disabled>
        Не жми
      </Button>,
    );

    await user.click(screen.getByRole('button'));

    expect(handleClick).not.toHaveBeenCalled();
  });
});
