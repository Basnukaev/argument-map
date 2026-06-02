import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SortSelect from './SortSelect';

const OPTIONS = [
  { value: 'recent', label: 'Сначала новые' },
  { value: 'alphabetical', label: 'По алфавиту' },
] as const;

describe('SortSelect', () => {
  it('combobox имеет дефолтный accessible name «Сортировка»', () => {
    render(<SortSelect value="recent" onChange={() => {}} options={OPTIONS} />);
    expect(
      screen.getByRole('combobox', { name: /Сортировка/ }),
    ).toBeInTheDocument();
  });

  it('кастомный label используется как accessible name', () => {
    render(
      <SortSelect
        value="recent"
        onChange={() => {}}
        options={OPTIONS}
        label="Порядок"
      />,
    );
    expect(screen.getByRole('combobox', { name: /Порядок/ })).toBeInTheDocument();
  });

  it('выбор опции вызывает onChange с её value', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<SortSelect value="recent" onChange={onChange} options={OPTIONS} />);
    await user.selectOptions(
      screen.getByRole('combobox', { name: /Сортировка/ }),
      'alphabetical',
    );
    expect(onChange).toHaveBeenCalledWith('alphabetical');
  });

  it('текущее value отражено в select', () => {
    render(
      <SortSelect value="alphabetical" onChange={() => {}} options={OPTIONS} />,
    );
    expect(screen.getByRole('combobox', { name: /Сортировка/ })).toHaveValue(
      'alphabetical',
    );
  });
});
