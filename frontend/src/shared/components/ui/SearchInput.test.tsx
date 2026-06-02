import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SearchInput from './SearchInput';

describe('SearchInput', () => {
  it('рендерит input с placeholder и aria-label', () => {
    render(
      <SearchInput
        value=""
        onChange={() => {}}
        placeholder="Поиск по книгам"
        ariaLabel="поиск книг"
      />,
    );
    const input = screen.getByRole('searchbox', { name: 'поиск книг' });
    expect(input).toHaveAttribute('placeholder', 'Поиск по книгам');
  });

  it('вызывает onChange при вводе', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<SearchInput value="" onChange={onChange} ariaLabel="поиск" />);
    await user.type(screen.getByRole('searchbox'), 'a');
    expect(onChange).toHaveBeenCalledWith('a');
  });

  it('clear-кнопка показывается только когда поле непустое', () => {
    const { rerender } = render(
      <SearchInput value="" onChange={() => {}} ariaLabel="поиск" />,
    );
    expect(screen.queryByRole('button', { name: 'Очистить' })).toBeNull();
    rerender(<SearchInput value="x" onChange={() => {}} ariaLabel="поиск" />);
    expect(screen.getByRole('button', { name: 'Очистить' })).toBeInTheDocument();
  });

  it('клик по clear вызывает onChange с пустой строкой', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<SearchInput value="abc" onChange={onChange} ariaLabel="поиск" />);
    await user.click(screen.getByRole('button', { name: 'Очистить' }));
    expect(onChange).toHaveBeenCalledWith('');
  });
});
