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

  it('бейдж статуса показывает русскую метку и цвет', () => {
    render(<NodeDetailsPanel node={makeNode({ status: 'DISPUTED' })} onClose={vi.fn()} />);
    const badge = screen.getByTestId('status-badge');
    expect(badge).toHaveTextContent('Спорный');
    expect(badge.className).toContain('bg-amber-100');
  });

  it('метаданные содержат дату создания и id автора', () => {
    render(
      <NodeDetailsPanel
        node={makeNode({
          createdAt: '2026-05-04T12:34:00Z',
          updatedAt: '2026-05-04T12:34:00Z',
          createdBy: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
        })}
        onClose={vi.fn()}
      />,
    );
    expect(screen.getByText(/мая 2026 г\./)).toBeInTheDocument();
    expect(screen.getByText('aaaaaaaa')).toBeInTheDocument();
  });

  it('строка "Обновлён" не показана если updatedAt совпадает с createdAt', () => {
    render(
      <NodeDetailsPanel
        node={makeNode({
          createdAt: '2026-05-04T10:00:00Z',
          updatedAt: '2026-05-04T10:00:00Z',
        })}
        onClose={vi.fn()}
      />,
    );
    expect(screen.queryByText('Обновлён')).not.toBeInTheDocument();
  });

  it('строка "Обновлён" показана если updatedAt отличается', () => {
    render(
      <NodeDetailsPanel
        node={makeNode({
          createdAt: '2026-05-04T10:00:00Z',
          updatedAt: '2026-05-05T11:00:00Z',
        })}
        onClose={vi.fn()}
      />,
    );
    expect(screen.getByText('Обновлён')).toBeInTheDocument();
  });
});
