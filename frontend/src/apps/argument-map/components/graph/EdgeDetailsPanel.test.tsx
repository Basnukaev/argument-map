import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import EdgeDetailsPanel from './EdgeDetailsPanel';
import type { components } from '@/shared/api/types';

type EdgeDto = components['schemas']['EdgeResponse'];
type NodeDto = components['schemas']['NodeResponse'];

const BASE = 'http://test.local';
const EDGE_ID = '99999999-9999-9999-9999-999999999999';
const FROM_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const TO_ID = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';

function makeEdge(over: Partial<EdgeDto> = {}): EdgeDto {
  return {
    id: EDGE_ID,
    fromNodeId: FROM_ID,
    toNodeId: TO_ID,
    edgeType: 'SUPPORTS',
    rationale: 'Старое обоснование',
    sourceHandle: 'right',
    targetHandle: 'left',
    createdBy: '12345678-aaaa-bbbb-cccc-dddddddddddd',
    createdAt: '2026-05-05T00:00:00Z',
    ...over,
  };
}

function makeNode(id: string, over: Partial<NodeDto> = {}): NodeDto {
  return {
    id,
    topicId: 'topic-1',
    nodeType: 'ARGUMENT',
    content: 'Содержание узла ' + id.slice(0, 4),
    status: 'STANDING',
    createdBy: 'user-1',
    createdAt: '2026-05-04T00:00:00Z',
    updatedAt: '2026-05-04T00:00:00Z',
    ...over,
  };
}

interface RenderOpts {
  edge?: EdgeDto;
  fromNode?: NodeDto;
  toNode?: NodeDto;
  initialEditing?: boolean;
}

function renderPanel(opts: RenderOpts = {}) {
  const onClose = vi.fn();
  const onUpdated = vi.fn();
  const result = render(
    <EdgeDetailsPanel
      edge={opts.edge ?? makeEdge()}
      fromNode={opts.fromNode ?? makeNode(FROM_ID, { nodeType: 'ARGUMENT' })}
      toNode={opts.toNode ?? makeNode(TO_ID, { nodeType: 'CLAIM' })}
      onClose={onClose}
      onUpdated={onUpdated}
      initialEditing={opts.initialEditing}
    />,
  );
  return { ...result, onClose, onUpdated };
}

describe('EdgeDetailsPanel', () => {
  it('показывает заголовок с типом связи и контекстный label', () => {
    renderPanel();
    expect(screen.getByRole('heading', { name: /Поддерживает/ })).toBeInTheDocument();
    // ARGUMENT -> CLAIM SUPPORTS = "поддерживает"
    expect(screen.getByText('поддерживает')).toBeInTheDocument();
  });

  it('рендерит превью from-узла и to-узла', () => {
    renderPanel({
      fromNode: makeNode(FROM_ID, { nodeType: 'ARGUMENT', content: 'Это довод' }),
      toNode: makeNode(TO_ID, { nodeType: 'CLAIM', content: 'Это тезис' }),
    });
    expect(screen.getByText('Это довод')).toBeInTheDocument();
    expect(screen.getByText('Это тезис')).toBeInTheDocument();
  });

  it('rationale показывается, если есть', () => {
    renderPanel({ edge: makeEdge({ rationale: 'Потому что хадис' }) });
    expect(screen.getByText('Потому что хадис')).toBeInTheDocument();
  });

  it('пустое rationale показывает (не указано)', () => {
    renderPanel({ edge: makeEdge({ rationale: undefined }) });
    expect(screen.getByText('(не указано)')).toBeInTheDocument();
  });

  it('крестик вызывает onClose', async () => {
    const { onClose } = renderPanel();
    await userEvent.click(screen.getByRole('button', { name: 'Закрыть панель' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('кнопка "Редактировать" переключает в edit-mode с radio-buttons из allowed', async () => {
    renderPanel();
    await userEvent.click(screen.getByRole('button', { name: /Редактировать/ }));
    // ARGUMENT -> CLAIM allowed: SUPPORTS, REFUTES
    expect(screen.getByLabelText(/Поддерживает/)).toBeChecked();
    expect(screen.getByLabelText(/Опровергает/)).toBeInTheDocument();
    // INVALIDATES не разрешён для пары ARGUMENT->CLAIM, не должно быть
    expect(screen.queryByLabelText(/Аннулирует/)).not.toBeInTheDocument();
  });

  it('initialEditing=true открывает панель сразу в edit mode', () => {
    renderPanel({ initialEditing: true });
    // textarea с rationale присутствует => edit-режим
    expect(screen.getByLabelText('Обоснование связи')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Сохранить/ })).toBeInTheDocument();
  });

  it('сохранение шлёт PATCH с только изменёнными полями и вызывает onUpdated', async () => {
    let capturedBody: unknown = null;
    server.use(
      http.patch(`${BASE}/api/v1/edges/${EDGE_ID}`, async ({ request }) => {
        capturedBody = await request.json();
        return HttpResponse.json(makeEdge({ edgeType: 'REFUTES' }));
      }),
    );

    const { onUpdated } = renderPanel({ initialEditing: true });
    // меняем тип SUPPORTS -> REFUTES, rationale не трогаем
    await userEvent.click(screen.getByLabelText(/Опровергает/));
    await userEvent.click(screen.getByRole('button', { name: /Сохранить/ }));

    await waitFor(() => expect(onUpdated).toHaveBeenCalledTimes(1));
    expect(capturedBody).toEqual({ edgeType: 'REFUTES' });
  });

  it('ошибка от бэка показывается и панель остаётся в edit mode', async () => {
    server.use(
      http.patch(`${BASE}/api/v1/edges/${EDGE_ID}`, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/invalid-edge',
            title: 'Невалидное ребро',
            status: 422,
            detail: 'Тип SUPPORTS недопустим',
          },
          { status: 422 },
        ),
      ),
    );

    renderPanel({ initialEditing: true });
    await userEvent.click(screen.getByLabelText(/Опровергает/));
    await userEvent.click(screen.getByRole('button', { name: /Сохранить/ }));

    await waitFor(() =>
      expect(screen.getByText(/Тип SUPPORTS недопустим/)).toBeInTheDocument(),
    );
    // всё ещё edit-mode
    expect(screen.getByRole('button', { name: /Сохранить/ })).toBeInTheDocument();
  });

  it('Отмена возвращает в view-mode без запросов', async () => {
    renderPanel({ initialEditing: true });
    await userEvent.click(screen.getByRole('button', { name: /Отмена/ }));
    // вернулись в view-mode: textarea ушла, появился snippet "Старое обоснование"
    expect(screen.queryByLabelText('Обоснование связи')).not.toBeInTheDocument();
    expect(screen.getByText('Старое обоснование')).toBeInTheDocument();
  });
});
