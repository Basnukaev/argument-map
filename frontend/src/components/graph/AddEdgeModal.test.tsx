import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import AddEdgeModal from './AddEdgeModal';
import type { components } from '@/api/types';

type NodeDto = components['schemas']['NodeResponse'];

const BASE = 'http://test.local';

beforeAll(() => {
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function () {
      this.open = true;
    };
  }
  if (!HTMLDialogElement.prototype.close) {
    HTMLDialogElement.prototype.close = function () {
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
  }
});

// По одному узлу каждого типа - даёт пары для всех ячеек матрицы из ADR-010
const NODES: NodeDto[] = [
  { id: 'n1', nodeType: 'QUESTION', content: 'Корневой вопрос' },
  { id: 'n2', nodeType: 'CLAIM', content: 'Тезис А' },
  { id: 'n3', nodeType: 'ARGUMENT', content: 'Аргумент за А' },
  { id: 'n4', nodeType: 'EVIDENCE', content: 'Свидетельство' },
];

function renderModal(props: Partial<Parameters<typeof AddEdgeModal>[0]> = {}) {
  const onClose = vi.fn();
  const onCreated = vi.fn();
  const result = render(
    <AddEdgeModal open={true} nodes={NODES} onClose={onClose} onCreated={onCreated} {...props} />,
  );
  return { ...result, onClose, onCreated };
}

describe('AddEdgeModal', () => {
  it('select "Куда" исключает выбранный узел из "Откуда"', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.selectOptions(screen.getByLabelText('Откуда'), 'n1');

    const toSelect = screen.getByLabelText('Куда') as HTMLSelectElement;
    const optionValues = Array.from(toSelect.options).map((o) => o.value);
    expect(optionValues).not.toContain('n1');
    expect(optionValues).toContain('n2');
    expect(optionValues).toContain('n3');
    expect(optionValues).toContain('n4');
  });

  it('успешный POST /edges с дефолтным SUPPORTS для разрешённой пары ARGUMENT→CLAIM', async () => {
    let received: unknown = null;
    server.use(
      http.post(`${BASE}/api/v1/edges`, async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ id: 'e1', fromNodeId: 'n3', toNodeId: 'n2' });
      }),
    );

    const user = userEvent.setup();
    const { onCreated, onClose } = renderModal();

    await user.selectOptions(screen.getByLabelText('Откуда'), 'n3');
    await user.selectOptions(screen.getByLabelText('Куда'), 'n2');

    await user.click(screen.getByRole('button', { name: 'Создать' }));

    await waitFor(() => {
      expect(onCreated).toHaveBeenCalledOnce();
    });
    expect(onClose).toHaveBeenCalledOnce();
    expect(received).toEqual({
      fromNodeId: 'n3',
      toNodeId: 'n2',
      edgeType: 'SUPPORTS',
    });
  });

  it('тип INVALIDATES сохраняется при сабмите для EVIDENCE→ARGUMENT', async () => {
    let received: unknown = null;
    server.use(
      http.post(`${BASE}/api/v1/edges`, async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ id: 'e1' });
      }),
    );

    const user = userEvent.setup();
    renderModal();

    await user.selectOptions(screen.getByLabelText('Откуда'), 'n4');
    await user.selectOptions(screen.getByLabelText('Куда'), 'n3');
    await user.click(screen.getByLabelText(/Аннулирует/i));
    await user.click(screen.getByRole('button', { name: 'Создать' }));

    await waitFor(() => {
      expect(received).toMatchObject({ edgeType: 'INVALIDATES' });
    });
  });

  it('блокирует сабмит когда узлы не выбраны', async () => {
    const user = userEvent.setup();
    const { onCreated } = renderModal();

    await user.click(screen.getByRole('button', { name: 'Создать' }));

    expect(onCreated).not.toHaveBeenCalled();
    // HTML5 required не даст сабмиту пройти
  });

  it('показывает 422 ошибку с detail', async () => {
    server.use(
      http.post(`${BASE}/api/v1/edges`, () =>
        HttpResponse.json(
          {
            type: 'https://example.com/errors/invalid-edge',
            title: 'Невалидное ребро',
            status: 422,
            detail: 'Узлы из разных тем',
          },
          { status: 422 },
        ),
      ),
    );

    const user = userEvent.setup();
    const { onCreated } = renderModal();

    await user.selectOptions(screen.getByLabelText('Откуда'), 'n3');
    await user.selectOptions(screen.getByLabelText('Куда'), 'n2');
    await user.click(screen.getByRole('button', { name: 'Создать' }));

    await waitFor(() => {
      expect(screen.getByText('Узлы из разных тем')).toBeInTheDocument();
    });
    expect(onCreated).not.toHaveBeenCalled();
  });

  it('запрещённая пара (CLAIM → ARGUMENT) показывает заглушку и блокирует Создать', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.selectOptions(screen.getByLabelText('Откуда'), 'n2'); // CLAIM
    await user.selectOptions(screen.getByLabelText('Куда'), 'n3'); // ARGUMENT

    expect(screen.getByText(/Эту пару узлов нельзя соединить/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Поддерживает/i)).not.toBeInTheDocument();
    const submit = screen.getByRole('button', { name: 'Создать' }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
  });

  it('фильтрует типы под пару QUESTION → CLAIM (только QUALIFIES)', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.selectOptions(screen.getByLabelText('Откуда'), 'n1'); // QUESTION
    await user.selectOptions(screen.getByLabelText('Куда'), 'n2'); // CLAIM

    expect(screen.getByLabelText(/Уточняет/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Поддерживает/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Опровергает/i)).not.toBeInTheDocument();
  });

  it('авто-переключает edgeType когда смена пары делает текущий выбор недопустимым', async () => {
    let received: unknown = null;
    server.use(
      http.post(`${BASE}/api/v1/edges`, async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ id: 'e1' });
      }),
    );

    const user = userEvent.setup();
    renderModal();

    // ARGUMENT → CLAIM: SUPPORTS разрешён (дефолт)
    await user.selectOptions(screen.getByLabelText('Откуда'), 'n3');
    await user.selectOptions(screen.getByLabelText('Куда'), 'n2');
    expect((screen.getByLabelText(/Поддерживает/i) as HTMLInputElement).checked).toBe(true);

    // меняем from на QUESTION: QUESTION → CLAIM запрещает SUPPORTS, остаётся QUALIFIES
    await user.selectOptions(screen.getByLabelText('Откуда'), 'n1');

    await waitFor(() => {
      expect((screen.getByLabelText(/Уточняет/i) as HTMLInputElement).checked).toBe(true);
    });

    await user.click(screen.getByRole('button', { name: 'Создать' }));
    await waitFor(() => {
      expect(received).toMatchObject({ edgeType: 'QUALIFIES' });
    });
  });
});
