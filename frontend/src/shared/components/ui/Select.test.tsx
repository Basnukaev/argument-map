import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Select from './Select';

const OPTIONS = [
  { value: 'a', label: 'Алиф' },
  { value: 'b', label: 'Ба' },
  { value: 'c', label: 'Та' },
] as const;

// jsdom не реализует Element.scrollIntoView - polyfill no-op
beforeAll(() => {
  if (!Element.prototype.scrollIntoView) {
    Element.prototype.scrollIntoView = function () {};
  }
});

describe('Select', () => {
  it('показывает label выбранной option на trigger', () => {
    render(<Select value="b" onChange={() => {}} options={OPTIONS} />);
    // Trigger button shows selected label
    expect(screen.getByRole('button')).toHaveTextContent('Ба');
  });

  it('пустой value → пустой label (defensive default)', () => {
    render(<Select value="missing" onChange={() => {}} options={OPTIONS} />);
    const button = screen.getByRole('button');
    // selected.find не нашёл - label="" т.е. button содержит только chevron
    expect(button.textContent?.trim()).toBe('');
  });

  it('click открывает listbox', async () => {
    const user = userEvent.setup();
    render(<Select value="a" onChange={() => {}} options={OPTIONS} />);

    expect(screen.queryByRole('listbox')).toBeNull();
    await user.click(screen.getByRole('button'));
    expect(screen.getByRole('listbox')).toBeInTheDocument();
    // Все опции видны
    expect(screen.getByRole('option', { name: 'Алиф' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Ба' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Та' })).toBeInTheDocument();
  });

  it('click опции вызывает onChange и закрывает listbox', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<Select value="a" onChange={onChange} options={OPTIONS} />);

    await user.click(screen.getByRole('button'));
    // Внутри role=option лежит <button> с onClick - кликаем по тексту
    // option'а который и есть в этом button
    await user.click(screen.getByText('Та'));

    expect(onChange).toHaveBeenCalledWith('c');
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('aria-expanded переключается при open/close', async () => {
    const user = userEvent.setup();
    render(<Select value="a" onChange={() => {}} options={OPTIONS} />);

    const button = screen.getByRole('button');
    expect(button).toHaveAttribute('aria-expanded', 'false');

    await user.click(button);
    expect(button).toHaveAttribute('aria-expanded', 'true');
  });

  it('aria-label передаётся на trigger', () => {
    render(
      <Select
        value="a"
        onChange={() => {}}
        options={OPTIONS}
        ariaLabel="выбор тома"
      />,
    );
    expect(screen.getByRole('button', { name: 'выбор тома' })).toBeInTheDocument();
  });

  it('Escape закрывает listbox', async () => {
    const user = userEvent.setup();
    render(<Select value="a" onChange={() => {}} options={OPTIONS} />);

    await user.click(screen.getByRole('button'));
    expect(screen.getByRole('listbox')).toBeInTheDocument();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('aria-selected="true" на выбранной опции в listbox', async () => {
    const user = userEvent.setup();
    render(<Select value="b" onChange={() => {}} options={OPTIONS} />);

    await user.click(screen.getByRole('button'));
    const selectedOption = screen.getByRole('option', { name: 'Ба' });
    expect(selectedOption).toHaveAttribute('aria-selected', 'true');
  });
});
