import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import AddEdgeModal from './AddEdgeModal';
import type { components } from '@/api/types';

type NodeDto = components['schemas']['NodeResponse'];
type UserEvent = ReturnType<typeof userEvent.setup>;

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
  { id: 'n4', nodeType: 'EVIDENCE', content: 'Хадис из аль-Бухари' },
];

function renderModal(props: Partial<Parameters<typeof AddEdgeModal>[0]> = {}) {
  const onClose = vi.fn();
  const onCreated = vi.fn();
  const result = render(
    <AddEdgeModal open={true} nodes={NODES} onClose={onClose} onCreated={onCreated} {...props} />,
  );
  return { ...result, onClose, onCreated };
}

/**
 * Открывает кастомный NodeSelect dropdown по lable, кликает option
 * с заданным content. Эквивалент userEvent.selectOptions для нативного select.
 */
async function pickNode(user: UserEvent, fieldLabel: string, optionContent: string) {
  await user.click(screen.getByLabelText(fieldLabel));
  const listbox = await screen.findByRole('listbox');
  await user.click(within(listbox).getByText(optionContent));
}

describe('AddEdgeModal', () => {
  it('"Куда" исключает выбранный в "Откуда" узел из своих опций', async () => {
    const user = userEvent.setup();
    renderModal();

    await pickNode(user, 'Откуда', 'Корневой вопрос'); // n1

    // открываем "Куда" - в списке не должно быть "Корневой вопрос"
    await user.click(screen.getByLabelText('Куда'));
    const listbox = await screen.findByRole('listbox');
    expect(within(listbox).queryByText('Корневой вопрос')).not.toBeInTheDocument();
    expect(within(listbox).getByText('Тезис А')).toBeInTheDocument();
    expect(within(listbox).getByText('Аргумент за А')).toBeInTheDocument();
    expect(within(listbox).getByText('Хадис из аль-Бухари')).toBeInTheDocument();
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

    await pickNode(user, 'Откуда', 'Аргумент за А'); // n3
    await pickNode(user, 'Куда', 'Тезис А'); // n2

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

    await pickNode(user, 'Откуда', 'Хадис из аль-Бухари'); // n4
    await pickNode(user, 'Куда', 'Аргумент за А'); // n3
    await user.click(screen.getByLabelText(/Аннулирует/i));
    await user.click(screen.getByRole('button', { name: 'Создать' }));

    await waitFor(() => {
      expect(received).toMatchObject({ edgeType: 'INVALIDATES' });
    });
  });

  it('блокирует сабмит когда узлы не выбраны', async () => {
    const user = userEvent.setup();
    const { onCreated } = renderModal();

    // Создать disabled пока пара не выбрана и не разрешена
    const submit = screen.getByRole('button', { name: 'Создать' }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
    await user.click(submit);

    expect(onCreated).not.toHaveBeenCalled();
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

    await pickNode(user, 'Откуда', 'Аргумент за А'); // n3
    await pickNode(user, 'Куда', 'Тезис А'); // n2
    await user.click(screen.getByRole('button', { name: 'Создать' }));

    await waitFor(() => {
      expect(screen.getByText('Узлы из разных тем')).toBeInTheDocument();
    });
    expect(onCreated).not.toHaveBeenCalled();
  });

  it('запрещённая пара (CLAIM → ARGUMENT) показывает заглушку и блокирует Создать', async () => {
    const user = userEvent.setup();
    renderModal();

    await pickNode(user, 'Откуда', 'Тезис А'); // CLAIM
    await pickNode(user, 'Куда', 'Аргумент за А'); // ARGUMENT

    expect(screen.getByText(/Эту пару узлов нельзя соединить/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Поддерживает/i)).not.toBeInTheDocument();
    const submit = screen.getByRole('button', { name: 'Создать' }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
  });

  it('фильтрует типы под пару QUESTION → CLAIM (только QUALIFIES)', async () => {
    const user = userEvent.setup();
    renderModal();

    await pickNode(user, 'Откуда', 'Корневой вопрос'); // QUESTION
    await pickNode(user, 'Куда', 'Тезис А'); // CLAIM

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
    await pickNode(user, 'Откуда', 'Аргумент за А'); // n3
    await pickNode(user, 'Куда', 'Тезис А'); // n2
    expect((screen.getByLabelText(/Поддерживает/i) as HTMLInputElement).checked).toBe(true);

    // меняем from на QUESTION: QUESTION → CLAIM запрещает SUPPORTS, остаётся QUALIFIES
    await pickNode(user, 'Откуда', 'Корневой вопрос'); // n1

    await waitFor(() => {
      expect((screen.getByLabelText(/Уточняет/i) as HTMLInputElement).checked).toBe(true);
    });

    await user.click(screen.getByRole('button', { name: 'Создать' }));
    await waitFor(() => {
      expect(received).toMatchObject({ edgeType: 'QUALIFIES' });
    });
  });

  it('initialFromId/initialToId предзаполняют поля для drag-create', () => {
    renderModal({ initialFromId: 'n3', initialToId: 'n2' });

    // триггеры показывают content выбранных узлов
    expect(screen.getByLabelText('Откуда')).toHaveTextContent('Аргумент за А');
    expect(screen.getByLabelText('Куда')).toHaveTextContent('Тезис А');
    // SUPPORTS - первый разрешённый для ARGUMENT → CLAIM, должен быть отмечен
    expect((screen.getByLabelText(/Поддерживает/i) as HTMLInputElement).checked).toBe(true);
  });

  it('только initialFromId - "Откуда" предзаполнено, "Куда" пусто (placeholder)', () => {
    renderModal({ initialFromId: 'n3' });
    expect(screen.getByLabelText('Откуда')).toHaveTextContent('Аргумент за А');
    // в "Куда" виден placeholder "- выбрать узел -"
    expect(screen.getByLabelText('Куда')).toHaveTextContent('- выбрать узел -');
  });

  it('initialSourceHandle/initialTargetHandle уезжают в POST /edges', async () => {
    let received: unknown = null;
    server.use(
      http.post(`${BASE}/api/v1/edges`, async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ id: 'e-handles' });
      }),
    );

    const user = userEvent.setup();
    renderModal({
      initialFromId: 'n3',
      initialToId: 'n2',
      initialSourceHandle: 'right',
      initialTargetHandle: 'left',
    });

    await user.click(screen.getByRole('button', { name: 'Создать' }));
    await waitFor(() => {
      expect(received).toMatchObject({
        fromNodeId: 'n3',
        toNodeId: 'n2',
        sourceHandle: 'right',
        targetHandle: 'left',
      });
    });
  });
});
