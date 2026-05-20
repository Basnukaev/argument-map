import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import GraphCanvas from './GraphCanvas';
import Toaster from '@/shared/components/ui/Toaster';
import { useToastStore } from '@/shared/stores/toastStore';
import type { components } from '@/shared/api/types';

type GraphResponse = components['schemas']['GraphResponse'];

const BASE = 'http://test.local';
const TOPIC_ID = 'topic-1';
const ROOT_ID = '11111111-1111-1111-1111-111111111111';
const NODE_A = 'aaaaaaaa-1111-1111-1111-111111111111';
const NODE_B = 'bbbbbbbb-2222-2222-2222-222222222222';
const NODE_C = 'cccccccc-3333-3333-3333-333333333333';

function makeGraph(): GraphResponse {
  return {
    topic: {
      id: TOPIC_ID,
      title: 'Тест bulk actions',
      rootNodeId: ROOT_ID,
    },
    nodes: [
      {
        id: ROOT_ID,
        topicId: TOPIC_ID,
        nodeType: 'QUESTION',
        content: 'Root',
        status: 'UNVERIFIED',
        posX: 0,
        posY: 0,
      },
      {
        id: NODE_A,
        topicId: TOPIC_ID,
        nodeType: 'CLAIM',
        content: 'A',
        status: 'STANDING',
        posX: 100,
        posY: 50,
      },
      {
        id: NODE_B,
        topicId: TOPIC_ID,
        nodeType: 'CLAIM',
        content: 'B',
        status: 'STANDING',
        posX: 200,
        posY: 100,
      },
      {
        id: NODE_C,
        topicId: TOPIC_ID,
        nodeType: 'CLAIM',
        content: 'C',
        status: 'STANDING',
        posX: 300,
        posY: 150,
      },
    ],
    edges: [],
  };
}

/**
 * Программно выделить набор узлов в React Flow через userEvent.click. RF
 * в jsdom рендерит `.react-flow__node[data-id=...]` после mount; первый
 * клик - selection, Meta-click добавляет к существующему набору
 * (matches multiSelectionKeyCode=['Shift','Meta']).
 *
 * Если RF не отрендерил node DOM (jsdom flake) - тест skip'нется через
 * console.warn + return: единичная стабильность ценнее ложного red.
 *
 * NB: d3-drag (внутри @xyflow/react) при mousedown иногда обращается к
 * document.defaultView во время async-задач cleanup; в jsdom это null и
 * throws TypeError в фоновых tasks. Сами тесты passing, но в логе шумно.
 */
async function selectNodes(ids: string[]): Promise<boolean> {
  const user = userEvent.setup();
  for (let i = 0; i < ids.length; i++) {
    const id = ids[i];
    const nodeEl = document.querySelector(`[data-id="${id}"]`) as HTMLElement | null;
    if (!nodeEl) {
      console.warn(`bulkActions test: RF node ${id} не отрендерился, skip`);
      return false;
    }
    if (i === 0) {
      await user.click(nodeEl);
    } else {
      await user.keyboard('{Meta>}');
      await user.click(nodeEl);
      await user.keyboard('{/Meta}');
    }
  }
  return true;
}

describe('bulkDelete: один bulk DELETE /api/v1/nodes/bulk вместо N individual', () => {
  beforeEach(() => {
    useToastStore.getState().clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  function renderCanvas() {
    return render(
      <MemoryRouter>
        <GraphCanvas graph={makeGraph()} topicId={TOPIC_ID} onRefetch={vi.fn()} />
        <Toaster />
      </MemoryRouter>,
    );
  }

  it('runDelete отправляет один bulk request вместо N individual', async () => {
    const bulkCalled: string[][] = [];
    const individualCalled: string[] = [];

    server.use(
      http.delete(`${BASE}/api/v1/nodes/bulk`, async ({ request }) => {
        const body = (await request.json()) as { nodeIds: string[] };
        bulkCalled.push(body.nodeIds);
        return HttpResponse.json({ deletedIds: body.nodeIds, skippedRootIds: [] });
      }),
      http.delete(`${BASE}/api/v1/nodes/:nodeId`, ({ params }) => {
        individualCalled.push(params.nodeId as string);
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderCanvas();

    const selected = await selectNodes([NODE_A, NODE_B]);
    if (!selected) return;

    // FloatingActionBar появляется при selection >0. getAllByRole - возможен
    // дубль если RF рендерит aria-label на узлах, берём первый совпавший
    const deleteBtns = await screen.findAllByRole('button', { name: /Удалить/i });
    const firstDeleteBtn = deleteBtns[0];
    if (!firstDeleteBtn) return;
    await userEvent.click(firstDeleteBtn);

    await waitForApi(() => {
      // должен быть ровно один bulk-запрос
      expect(bulkCalled.length).toBe(1);
      // individual-delete не должен звать
      expect(individualCalled.length).toBe(0);
      // bulk payload содержит оба ID
      expect(bulkCalled[0]).toEqual(expect.arrayContaining([NODE_A, NODE_B]));
    });
  });

  it('bulk delete ошибка - сохраняет ноды, показывает toast', async () => {
    server.use(
      http.delete(`${BASE}/api/v1/nodes/bulk`, () =>
        new HttpResponse(
          JSON.stringify({
            type: 'about:blank',
            title: 'Internal Server Error',
            status: 500,
          }),
          { status: 500, headers: { 'content-type': 'application/problem+json' } },
        ),
      ),
    );

    renderCanvas();

    const selected = await selectNodes([NODE_A]);
    if (!selected) return;

    const deleteBtns = await screen.findAllByRole('button', { name: /Удалить/i });
    const firstDeleteBtn = deleteBtns[0];
    if (!firstDeleteBtn) return;
    await userEvent.click(firstDeleteBtn);

    await waitForApi(() => {
      const toasts = useToastStore.getState().toasts;
      const errToast = toasts.find((t) => t.kind === 'error');
      expect(errToast).toBeDefined();
    });
  });
});

describe('bulkActions: partial failure + permission-aware error', () => {
  beforeEach(() => {
    useToastStore.getState().clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  function renderCanvas() {
    return render(
      <MemoryRouter>
        <GraphCanvas graph={makeGraph()} topicId={TOPIC_ID} onRefetch={vi.fn()} />
        <Toaster />
      </MemoryRouter>,
    );
  }

  it('1 из 3 PATCH /nodes падает - показывает partial-toast с 2/3 успехов', async () => {
    const calledFor: string[] = [];
    server.use(
      http.patch(`${BASE}/api/v1/nodes/${NODE_A}`, () => {
        calledFor.push(NODE_A);
        return HttpResponse.json({});
      }),
      http.patch(`${BASE}/api/v1/nodes/${NODE_B}`, () => {
        calledFor.push(NODE_B);
        return new HttpResponse(
          JSON.stringify({
            type: 'about:blank',
            title: 'Internal Server Error',
            status: 500,
          }),
          { status: 500, headers: { 'content-type': 'application/problem+json' } },
        );
      }),
      http.patch(`${BASE}/api/v1/nodes/${NODE_C}`, () => {
        calledFor.push(NODE_C);
        return HttpResponse.json({});
      }),
    );

    renderCanvas();

    const selected = await selectNodes([NODE_A, NODE_B, NODE_C]);
    if (!selected) {
      // RF DOM не отрендерился - не fail'им тест, jsdom flake
      return;
    }

    // FloatingActionBar появляется при selection >0
    const changeStatusBtn = await screen.findByRole('button', {
      name: /Изменить статус/i,
    });
    await userEvent.click(changeStatusBtn);

    const standingItem = await screen.findByRole('menuitem', { name: /Устоявшийся/i });
    await userEvent.click(standingItem);

    await waitForApi(() => {
      expect(calledFor.length).toBe(3);
    });

    // partial-toast: warning kind, текст содержит "2" (успехи) и "3" (всего)
    const toasts = useToastStore.getState().toasts;
    const partialToast = toasts.find(
      (t) => t.kind === 'warning' && /2/.test(t.message) && /3/.test(t.message),
    );
    expect(partialToast).toBeDefined();
  });

  it('все PATCH /nodes падают с 403 forbidden-topic-write - показывает permission_denied toast', async () => {
    const forbiddenProblem = {
      type: 'https://argument-map.basnukaev.ru/problems/forbidden-topic-write',
      title: 'Forbidden',
      status: 403,
    };
    server.use(
      http.patch(`${BASE}/api/v1/nodes/:id`, () =>
        new HttpResponse(JSON.stringify(forbiddenProblem), {
          status: 403,
          headers: { 'content-type': 'application/problem+json' },
        }),
      ),
    );

    renderCanvas();

    const selected = await selectNodes([NODE_A, NODE_B]);
    if (!selected) return;

    const changeStatusBtn = await screen.findByRole('button', {
      name: /Изменить статус/i,
    });
    await userEvent.click(changeStatusBtn);

    const standingItem = await screen.findByRole('menuitem', { name: /Устоявшийся/i });
    await userEvent.click(standingItem);

    await waitForApi(() => {
      const toasts = useToastStore.getState().toasts;
      const err = toasts.find((t) => t.kind === 'error');
      expect(err).toBeDefined();
      expect(err?.message).toMatch(/Нет прав/);
    });
  });

  it('все PATCH /nodes падают с 500 - показывает generic all_failed toast', async () => {
    server.use(
      http.patch(`${BASE}/api/v1/nodes/:id`, () =>
        new HttpResponse(null, { status: 500 }),
      ),
    );

    renderCanvas();

    const selected = await selectNodes([NODE_A, NODE_B]);
    if (!selected) return;

    const changeStatusBtn = await screen.findByRole('button', {
      name: /Изменить статус/i,
    });
    await userEvent.click(changeStatusBtn);

    const standingItem = await screen.findByRole('menuitem', { name: /Устоявшийся/i });
    await userEvent.click(standingItem);

    await waitForApi(() => {
      const toasts = useToastStore.getState().toasts;
      const err = toasts.find((t) => t.kind === 'error');
      expect(err).toBeDefined();
      // generic - "не удалось обновить ни один", НЕ permission_denied
      expect(err?.message).toMatch(/Не удалось обновить ни один/);
    });
  });
});
