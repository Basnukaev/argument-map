import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import NodeDetailsPanel from './NodeDetailsPanel';
import type { components } from '@/api/types';

type NodeDto = components['schemas']['NodeResponse'];

function makeNode(over: Partial<NodeDto> = {}): NodeDto {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    topicId: 'topic-1',
    nodeType: 'CLAIM',
    content: 'Тестовый тезис',
    status: 'STANDING',
    createdBy: 'user-1',
    createdAt: '2026-05-05T00:00:00Z',
    updatedAt: '2026-05-05T00:00:00Z',
    ...over,
  };
}

describe('NodeDetailsPanel', () => {
  it('показывает заголовок с типом и содержание', () => {
    render(<NodeDetailsPanel node={makeNode({ nodeType: 'ARGUMENT', content: 'Текст довода' })} onClose={vi.fn()} />);
    expect(screen.getByRole('heading', { name: /Довод/ })).toBeInTheDocument();
    expect(screen.getByText('Текст довода')).toBeInTheDocument();
  });

  it('пустой контент показывает (пусто)', () => {
    render(<NodeDetailsPanel node={makeNode({ content: '' })} onClose={vi.fn()} />);
    expect(screen.getByText('(пусто)')).toBeInTheDocument();
  });

  it('крестик вызывает onClose', async () => {
    const onClose = vi.fn();
    render(<NodeDetailsPanel node={makeNode()} onClose={onClose} />);
    await userEvent.click(screen.getByRole('button', { name: 'Закрыть панель' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('aria-label панели = Детали узла', () => {
    render(<NodeDetailsPanel node={makeNode()} onClose={vi.fn()} />);
    expect(screen.getByRole('complementary', { name: 'Детали узла' })).toBeInTheDocument();
  });
});
