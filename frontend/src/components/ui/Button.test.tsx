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
    expect(screen.getByRole('button')).toHaveClass('bg-gray-200');
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
