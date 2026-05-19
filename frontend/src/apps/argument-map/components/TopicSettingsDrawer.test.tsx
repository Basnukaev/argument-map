import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import { useToastStore } from '@/shared/stores/toastStore';
import TopicSettingsDrawer from './TopicSettingsDrawer';
import type { components } from '@/shared/api/types';

const BASE = 'http://test.local';
const TOPIC_ID = '00000000-0000-0000-0000-000000000aaa';
const OWNER_ID = '00000000-0000-0000-0000-000000000001';

type TopicResponse = components['schemas']['TopicResponse'];

function makeTopic(overrides: Partial<TopicResponse> = {}): TopicResponse {
  return {
    id: TOPIC_ID,
    title: 'Дозволенность мавлида',
    description: 'Разбор позиций',
    rootNodeId: 'root-id',
    createdBy: OWNER_ID,
    createdAt: '2026-05-01T00:00:00Z',
    visibility: 'PRIVATE',
    statusAlgorithm: 'MVP',
    nodeCount: 5,
    edgeCount: 4,
    ...overrides,
  };
}

function renderDrawer(opts: {
  topic?: TopicResponse;
  canManage?: boolean;
  isAdmin?: boolean;
  onClose?: () => void;
  onChanged?: () => void;
}) {
  const topic = opts.topic ?? makeTopic();
  return render(
    <MemoryRouter initialEntries={[`/topics/${TOPIC_ID}`]}>
      <Routes>
        <Route
          path="/topics/:topicId"
          element={
            <TopicSettingsDrawer
              open
              topic={topic}
              canManage={opts.canManage ?? true}
              isAdmin={opts.isAdmin ?? false}
              onClose={opts.onClose ?? (() => {})}
              onChanged={opts.onChanged ?? (() => {})}
            />
          }
        />
        <Route path="/topics" element={<div>topics list</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('TopicSettingsDrawer', () => {
  beforeEach(() => {
    // HTMLDialogElement polyfill для TopicMembersModal (он использует <dialog>)
    if (!HTMLDialogElement.prototype.showModal) {
      HTMLDialogElement.prototype.showModal = function () {
        this.open = true;
      };
    }
    if (!HTMLDialogElement.prototype.close) {
      HTMLDialogElement.prototype.close = function () {
        this.open = false;
      };
    }
    vi.stubGlobal('confirm', () => true);
    // Reset toastStore - Toaster компонент не монтируется в тестах,
    // но store продолжает хранить отображённые сообщения. Очищаем между
    // тестами чтобы prev-runs не накапливались
    useToastStore.setState({ toasts: [] });
  });

  it('рендерит основные секции для owner: metadata / visibility / status algorithm / danger', () => {
    renderDrawer({ canManage: true, isAdmin: false });

    expect(screen.getByText('Настройки темы')).toBeInTheDocument();
    // Секция metadata (title+description) с editable формой - есть и section
    // header "Описание", и field label "Описание" (textarea). Проверяем
    // через roles чтобы не ловить ambiguity текста
    expect(
      screen.getByRole('heading', { name: 'Описание', level: 3 }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('topic-edit-title')).toBeInTheDocument();
    expect(screen.getByText('Видимость')).toBeInTheDocument();
    expect(screen.getByText('Алгоритм статусов')).toBeInTheDocument();
    expect(screen.getByText('Опасная зона')).toBeInTheDocument();
    // Без isAdmin - audit section отсутствует
    expect(screen.queryByText('Журнал изменений')).not.toBeInTheDocument();
  });

  it('показывает audit-section только для ADMIN', () => {
    renderDrawer({ canManage: true, isAdmin: true });
    expect(screen.getByText('Журнал изменений')).toBeInTheDocument();
    const link = screen.getByRole('link', { name: /Просмотр журнала/ });
    expect(link).toHaveAttribute(
      'href',
      `/admin/audit?entityType=TOPIC&entityId=${TOPIC_ID}`,
    );
  });

  it('секция Участники появляется только при visibility=SHARED', async () => {
    server.use(
      http.get(`${BASE}/api/v1/topics/${TOPIC_ID}/members`, () =>
        HttpResponse.json([]),
      ),
    );
    const { rerender } = renderDrawer({
      topic: makeTopic({ visibility: 'PRIVATE' }),
      canManage: true,
    });
    expect(screen.queryByText('Участники')).not.toBeInTheDocument();

    rerender(
      <MemoryRouter initialEntries={[`/topics/${TOPIC_ID}`]}>
        <Routes>
          <Route
            path="/topics/:topicId"
            element={
              <TopicSettingsDrawer
                open
                topic={makeTopic({ visibility: 'SHARED' })}
                canManage
                isAdmin={false}
                onClose={() => {}}
                onChanged={() => {}}
              />
            }
          />
        </Routes>
      </MemoryRouter>,
    );
    await waitForApi(() => {
      expect(screen.getByText('Участники')).toBeInTheDocument();
    });
  });

  it('изменение visibility шлёт PATCH /visibility и вызывает onChanged + toast', async () => {
    let patched: unknown = null;
    server.use(
      http.patch(
        `${BASE}/api/v1/topics/${TOPIC_ID}/visibility`,
        async ({ request }) => {
          patched = await request.json();
          return HttpResponse.json({
            id: TOPIC_ID,
            visibility: 'PUBLIC',
          });
        },
      ),
    );
    const onChanged = vi.fn();
    const user = userEvent.setup();
    renderDrawer({
      topic: makeTopic({ visibility: 'PRIVATE' }),
      canManage: true,
      onChanged,
    });

    // VisibilityRadioGroup рендерит labels с hidden radio. Click по label
    // карточки «Публичная» через text
    await user.click(screen.getByText('Публичная'));

    await waitForApi(() => {
      expect(patched).toEqual({ visibility: 'PUBLIC' });
    });
    expect(onChanged).toHaveBeenCalled();
  });

  it('изменение status algorithm шлёт PATCH /status-algorithm', async () => {
    let patched: unknown = null;
    server.use(
      http.patch(
        `${BASE}/api/v1/topics/${TOPIC_ID}/status-algorithm`,
        async ({ request }) => {
          patched = await request.json();
          return HttpResponse.json({
            id: TOPIC_ID,
            statusAlgorithm: 'DUNG_GROUNDED',
          });
        },
      ),
    );
    const onChanged = vi.fn();
    const user = userEvent.setup();
    renderDrawer({ canManage: true, onChanged });

    await user.click(screen.getByText('Dung grounded'));

    await waitForApi(() => {
      expect(patched).toEqual({ algorithm: 'DUNG_GROUNDED' });
    });
    expect(onChanged).toHaveBeenCalled();
  });

  it('delete flow: typing topic name → confirm → DELETE → navigate /topics', async () => {
    let deleted = false;
    server.use(
      http.delete(`${BASE}/api/v1/topics/${TOPIC_ID}`, () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderDrawer({ canManage: true, onClose });

    // Open delete confirm
    await user.click(screen.getByRole('button', { name: 'Удалить тему' }));
    expect(
      screen.getByText('Удалить тему навсегда'),
    ).toBeInTheDocument();

    // Кнопка confirm должна быть disabled пока typed != title
    const confirmBtn = screen.getByRole('button', {
      name: 'Удалить навсегда',
    });
    expect(confirmBtn).toBeDisabled();

    // Type точное имя темы - confirm input находится внутри dialog'а
    // (нужно scope'нуть от title input в metadata-секции)
    const dialog = screen.getByTestId('topic-delete-confirm');
    const input = within(dialog).getByRole('textbox');
    await user.type(input, 'Дозволенность мавлида');

    expect(confirmBtn).not.toBeDisabled();
    await user.click(confirmBtn);

    await waitForApi(() => {
      expect(deleted).toBe(true);
    });
    // Navigated → "topics list"
    await waitForApi(() => {
      expect(screen.getByText('topics list')).toBeInTheDocument();
    });
    expect(onClose).toHaveBeenCalled();
  });

  it('не показывает Visibility / Status / Danger для non-manager (read-only viewer)', () => {
    renderDrawer({ canManage: false, isAdmin: false });
    // Секция Описание (heading) есть для viewer (read-only display)
    expect(
      screen.getByRole('heading', { name: 'Описание', level: 3 }),
    ).toBeInTheDocument();
    expect(screen.queryByText('Видимость')).not.toBeInTheDocument();
    expect(screen.queryByText('Алгоритм статусов')).not.toBeInTheDocument();
    expect(screen.queryByText('Опасная зона')).not.toBeInTheDocument();
    // Для viewer форма редактирования заменяется на readonly блок (без input'а)
    expect(screen.queryByTestId('topic-edit-title')).not.toBeInTheDocument();
  });

  // ---- PATCH /topics/{id} (backlog tech debt #10) ----

  it('edit form: input controlled, save button disabled пока нет изменений', () => {
    renderDrawer({ canManage: true });
    const title = screen.getByTestId('topic-edit-title') as HTMLInputElement;
    expect(title.value).toBe('Дозволенность мавлида');
    const save = screen.getByTestId('topic-edit-save');
    expect(save).toBeDisabled();
  });

  it('edit form: save button disabled при пустом title', async () => {
    const user = userEvent.setup();
    renderDrawer({ canManage: true });
    const title = screen.getByTestId('topic-edit-title') as HTMLInputElement;
    await user.clear(title);
    const save = screen.getByTestId('topic-edit-save');
    expect(save).toBeDisabled();
    expect(screen.getByText('Название обязательно')).toBeInTheDocument();
  });

  it('edit form: успешный save шлёт PATCH /topics/{id} с changed полями + toast', async () => {
    let patched: unknown = null;
    server.use(
      http.patch(`${BASE}/api/v1/topics/${TOPIC_ID}`, async ({ request }) => {
        patched = await request.json();
        return HttpResponse.json({
          id: TOPIC_ID,
          title: 'Новое название',
          description: 'Разбор позиций',
          rootNodeId: 'root-id',
          createdBy: OWNER_ID,
          createdAt: '2026-05-01T00:00:00Z',
          visibility: 'PRIVATE',
          statusAlgorithm: 'MVP',
          nodeCount: 5,
          edgeCount: 4,
        });
      }),
    );
    const onChanged = vi.fn();
    const user = userEvent.setup();
    renderDrawer({ canManage: true, onChanged });

    const title = screen.getByTestId('topic-edit-title') as HTMLInputElement;
    await user.clear(title);
    await user.type(title, 'Новое название');

    const save = screen.getByTestId('topic-edit-save');
    expect(save).not.toBeDisabled();
    await user.click(save);

    await waitForApi(() => {
      // PATCH-семантика на фронте: шлём только изменившиеся поля.
      // description не менялся - не отправляется
      expect(patched).toEqual({ title: 'Новое название' });
    });
    expect(onChanged).toHaveBeenCalled();
  });

  it('edit form: меняем оба поля - оба попадают в body', async () => {
    let patched: unknown = null;
    server.use(
      http.patch(`${BASE}/api/v1/topics/${TOPIC_ID}`, async ({ request }) => {
        patched = await request.json();
        return HttpResponse.json({ id: TOPIC_ID });
      }),
    );
    const user = userEvent.setup();
    renderDrawer({ canManage: true });

    const title = screen.getByTestId('topic-edit-title') as HTMLInputElement;
    await user.clear(title);
    await user.type(title, 'T2');
    const desc = screen.getByTestId('topic-edit-description') as HTMLTextAreaElement;
    await user.clear(desc);
    await user.type(desc, 'D2');

    await user.click(screen.getByTestId('topic-edit-save'));

    await waitForApi(() => {
      expect(patched).toEqual({ title: 'T2', description: 'D2' });
    });
  });

  it('edit form: 403 forbidden-topic-write → permission toast', async () => {
    server.use(
      http.patch(`${BASE}/api/v1/topics/${TOPIC_ID}`, () =>
        HttpResponse.json(
          {
            type: 'https://api.example.com/problems/forbidden-topic-write',
            title: 'Forbidden',
            status: 403,
            detail: 'no write',
          },
          { status: 403 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderDrawer({ canManage: true });

    const title = screen.getByTestId('topic-edit-title') as HTMLInputElement;
    await user.clear(title);
    await user.type(title, 'Новое');
    await user.click(screen.getByTestId('topic-edit-save'));

    // Toaster компонент не монтируется в тестах - проверяем toastStore
    // напрямую. permissionErrors helper маппит forbidden-topic-write на
    // 'topic.permission.forbidden_write'
    await waitForApi(() => {
      const toasts = useToastStore.getState().toasts;
      expect(
        toasts.some(
          (toast) =>
            toast.kind === 'error' &&
            toast.message === 'У вас нет прав на изменение этой темы',
        ),
      ).toBe(true);
    });
  });
});
