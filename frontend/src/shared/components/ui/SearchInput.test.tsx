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

  it('без пропа arabicKeyboard тогла клавиатуры нет (регрессия-гард)', () => {
    render(<SearchInput value="" onChange={() => {}} ariaLabel="поиск" />);
    expect(
      screen.queryByRole('button', { name: 'Арабская клавиатура' }),
    ).toBeNull();
  });

  it('с arabicKeyboard есть тогл, клик открывает попап клавиатуры', async () => {
    const user = userEvent.setup();
    render(
      <SearchInput value="" onChange={() => {}} ariaLabel="поиск" arabicKeyboard />,
    );
    const toggle = screen.getByRole('button', { name: 'Арабская клавиатура' });
    expect(screen.queryByRole('dialog')).toBeNull();
    await user.click(toggle);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('клик по букве в клавиатуре добавляет её в значение (onChange с value+буква)', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(
      <SearchInput value="ا" onChange={onChange} ariaLabel="поиск" arabicKeyboard />,
    );
    await user.click(screen.getByRole('button', { name: 'Арабская клавиатура' }));
    await user.click(screen.getByRole('button', { name: 'ب' }));
    expect(onChange).toHaveBeenCalledWith('اب');
  });

  it('backspace в клавиатуре укорачивает значение на один символ', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(
      <SearchInput value="اب" onChange={onChange} ariaLabel="поиск" arabicKeyboard />,
    );
    await user.click(screen.getByRole('button', { name: 'Арабская клавиатура' }));
    await user.click(screen.getByRole('button', { name: 'Стереть' }));
    expect(onChange).toHaveBeenCalledWith('ا');
  });
});
