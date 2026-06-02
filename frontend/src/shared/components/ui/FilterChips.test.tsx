import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import FilterChips from './FilterChips';

const OPTIONS = [
  { value: 'ALL', label: 'Все' },
  { value: 'OPEN', label: 'Открытые', count: 5 },
  { value: 'CLOSED', label: 'Закрытые', count: 0 },
] as const;

describe('FilterChips', () => {
  it('рендерит все опции как кнопки', () => {
    render(<FilterChips options={OPTIONS} value="ALL" onChange={() => {}} />);
    expect(screen.getByRole('button', { name: /Все/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Открытые/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Закрытые/ })).toBeInTheDocument();
  });

  it('активная пилюля имеет aria-pressed=true', () => {
    render(<FilterChips options={OPTIONS} value="OPEN" onChange={() => {}} />);
    expect(screen.getByRole('button', { name: /Открытые/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByRole('button', { name: /Все/ })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
  });

  it('клик по пилюле вызывает onChange с её value', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<FilterChips options={OPTIONS} value="ALL" onChange={onChange} />);
    await user.click(screen.getByRole('button', { name: /Открытые/ }));
    expect(onChange).toHaveBeenCalledWith('OPEN');
  });

  it('показывает count бейджем когда передан', () => {
    render(<FilterChips options={OPTIONS} value="ALL" onChange={() => {}} />);
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('0')).toBeInTheDocument();
  });

  it('передаёт ariaLabel в group', () => {
    render(
      <FilterChips
        options={OPTIONS}
        value="ALL"
        onChange={() => {}}
        ariaLabel="фильтр статуса"
      />,
    );
    expect(screen.getByRole('group', { name: 'фильтр статуса' })).toBeInTheDocument();
  });
});
