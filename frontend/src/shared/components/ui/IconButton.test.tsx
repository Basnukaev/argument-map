import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Plus } from 'lucide-react';
import IconButton from './IconButton';

describe('IconButton', () => {
  it('label передаётся в aria-label и title (a11y)', () => {
    render(<IconButton icon={Plus} label="Добавить узел" />);
    const btn = screen.getByRole('button', { name: 'Добавить узел' });
    expect(btn).toHaveAttribute('aria-label', 'Добавить узел');
    expect(btn).toHaveAttribute('title', 'Добавить узел');
  });

  it('срабатывает onClick по клику', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(<IconButton icon={Plus} label="Add" onClick={onClick} />);
    await user.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('disabled блокирует клик', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(<IconButton icon={Plus} label="Add" onClick={onClick} disabled />);
    await user.click(screen.getByRole('button'));
    expect(onClick).not.toHaveBeenCalled();
  });

  it('active=true применяет accent-классы', () => {
    render(<IconButton icon={Plus} label="Add" active />);
    expect(screen.getByRole('button')).toHaveClass('bg-accent-50');
  });

  it('variant=solid применяет solid-классы вместо ghost', () => {
    render(<IconButton icon={Plus} label="Add" variant="solid" />);
    expect(screen.getByRole('button')).toHaveClass('bg-elevated');
  });

  it('size="sm" применяет h-7 w-7 классы', () => {
    render(<IconButton icon={Plus} label="Add" size="sm" />);
    const btn = screen.getByRole('button');
    expect(btn).toHaveClass('h-7');
    expect(btn).toHaveClass('w-7');
  });

  it('тип кнопки type="button" - не submit по умолчанию', () => {
    render(<IconButton icon={Plus} label="Add" />);
    expect(screen.getByRole('button')).toHaveAttribute('type', 'button');
  });
});
