import { describe, it, expect, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import NodeSelect from './NodeSelect';
import type { components } from '@/api/types';

type NodeDto = components['schemas']['NodeResponse'];

const NODES: NodeDto[] = [
  { id: 'n1', nodeType: 'QUESTION', content: 'Корневой вопрос', status: 'STANDING' },
  { id: 'n2', nodeType: 'CLAIM', content: 'Тезис А', status: 'DISPUTED' },
  { id: 'n3', nodeType: 'ARGUMENT', content: 'Аргумент за А', status: 'UNVERIFIED' },
  { id: 'n4', nodeType: 'EVIDENCE', content: 'Хадис из аль-Бухари', status: 'STANDING' },
];

describe('NodeSelect', () => {
  it('триггер показывает placeholder когда value пустой', () => {
    render(<NodeSelect value="" onChange={() => {}} options={NODES} />);
    expect(screen.getByRole('button')).toHaveTextContent('- выбрать узел -');
  });

  it('триггер показывает текст выбранного узла', () => {
    render(<NodeSelect value="n2" onChange={() => {}} options={NODES} />);
    expect(screen.getByRole('button')).toHaveTextContent('Тезис А');
  });

  it('кнопка открывает dropdown со всеми опциями', async () => {
    const user = userEvent.setup();
    render(<NodeSelect value="" onChange={() => {}} options={NODES} />);

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button'));
    const listbox = screen.getByRole('listbox');
    expect(within(listbox).getByText('Корневой вопрос')).toBeInTheDocument();
    expect(within(listbox).getByText('Тезис А')).toBeInTheDocument();
    expect(within(listbox).getByText('Аргумент за А')).toBeInTheDocument();
    expect(within(listbox).getByText('Хадис из аль-Бухари')).toBeInTheDocument();
  });

  it('клик по опции вызывает onChange и закрывает dropdown', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<NodeSelect value="" onChange={onChange} options={NODES} />);

    await user.click(screen.getByRole('button'));
    await user.click(within(screen.getByRole('listbox')).getByText('Аргумент за А'));

    expect(onChange).toHaveBeenCalledWith('n3');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('excludeId исключает узел из списка', async () => {
    const user = userEvent.setup();
    render(<NodeSelect value="" onChange={() => {}} options={NODES} excludeId="n3" />);

    await user.click(screen.getByRole('button'));
    const listbox = screen.getByRole('listbox');
    expect(within(listbox).queryByText('Аргумент за А')).not.toBeInTheDocument();
    expect(within(listbox).getByText('Корневой вопрос')).toBeInTheDocument();
  });

  it('пустой список опций показывает заглушку', async () => {
    const user = userEvent.setup();
    render(<NodeSelect value="" onChange={() => {}} options={[]} />);

    await user.click(screen.getByRole('button'));
    expect(screen.getByText('Нет доступных узлов')).toBeInTheDocument();
  });

  it('Escape закрывает dropdown', async () => {
    const user = userEvent.setup();
    render(<NodeSelect value="" onChange={() => {}} options={NODES} />);

    await user.click(screen.getByRole('button'));
    expect(screen.getByRole('listbox')).toBeInTheDocument();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('disabled запрещает открытие', async () => {
    const user = userEvent.setup();
    render(<NodeSelect value="" onChange={() => {}} options={NODES} disabled />);

    await user.click(screen.getByRole('button'));
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('aria-expanded меняется при открытии/закрытии', async () => {
    const user = userEvent.setup();
    render(<NodeSelect value="" onChange={() => {}} options={NODES} />);
    const button = screen.getByRole('button');

    expect(button).toHaveAttribute('aria-expanded', 'false');
    await user.click(button);
    expect(button).toHaveAttribute('aria-expanded', 'true');
    await user.keyboard('{Escape}');
    expect(button).toHaveAttribute('aria-expanded', 'false');
  });
});
