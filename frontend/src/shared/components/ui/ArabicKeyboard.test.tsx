import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ArabicKeyboard from './ArabicKeyboard';

describe('ArabicKeyboard', () => {
  it('рендерит арабские буквы алфавита', () => {
    render(
      <ArabicKeyboard onInsert={() => {}} onBackspace={() => {}} onClose={() => {}} />,
    );
    // алфавит (ا) + вариант (لا) присутствуют как кнопки
    expect(screen.getByRole('button', { name: 'ا' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'ي' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'لا' })).toBeInTheDocument();
  });

  it('клик по букве вызывает onInsert с этой буквой', async () => {
    const onInsert = vi.fn();
    const user = userEvent.setup();
    render(
      <ArabicKeyboard onInsert={onInsert} onBackspace={() => {}} onClose={() => {}} />,
    );
    await user.click(screen.getByRole('button', { name: 'ب' }));
    expect(onInsert).toHaveBeenCalledWith('ب');
  });

  it('пробел вызывает onInsert с пробелом', async () => {
    const onInsert = vi.fn();
    const user = userEvent.setup();
    render(
      <ArabicKeyboard onInsert={onInsert} onBackspace={() => {}} onClose={() => {}} />,
    );
    await user.click(screen.getByRole('button', { name: 'Пробел' }));
    expect(onInsert).toHaveBeenCalledWith(' ');
  });

  it('backspace вызывает onBackspace', async () => {
    const onBackspace = vi.fn();
    const user = userEvent.setup();
    render(
      <ArabicKeyboard onInsert={() => {}} onBackspace={onBackspace} onClose={() => {}} />,
    );
    await user.click(screen.getByRole('button', { name: 'Стереть' }));
    expect(onBackspace).toHaveBeenCalledTimes(1);
  });

  it('кнопка закрытия вызывает onClose', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(
      <ArabicKeyboard onInsert={() => {}} onBackspace={() => {}} onClose={onClose} />,
    );
    await user.click(screen.getByRole('button', { name: 'Закрыть' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('Escape вызывает onClose', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(
      <ArabicKeyboard onInsert={() => {}} onBackspace={() => {}} onClose={onClose} />,
    );
    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
